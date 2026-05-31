package com.backend.auth.service;

import com.backend.auth.domain.dto.request.*;
import com.backend.auth.domain.dto.response.AuthResponse;
import com.backend.auth.domain.entity.*;
import com.backend.auth.event.UserDeletionEvent;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import com.backend.auth.repository.RefreshTokenRepository;
import com.backend.auth.repository.UserRepository;
import com.backend.auth.repository.SocialAccountRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final RegistrationOrchestrationService registrationOrchestrationService;
    private final PasswordEncoder passwordEncoder;
    private final SocialAccountRepository socialAccountRepository;
    private final OutboxService outboxService;

    @Value("${app.jwt.refresh-token-expiration-seconds}")
    private long refreshTokenExpirationSeconds;

    @Value("${app.oauth2.google.client-id}")
    private String googleClientId;

    private GoogleIdTokenVerifier googleVerifier;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @PostConstruct
    public void init() {
        // Build Google verifier once at startup — creating it per-request is expensive
        googleVerifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    // -----------------------------------------------------------
    // Social Login (Google)
    // If account doesn't exist, auto-create it (first login = register)
    // -----------------------------------------------------------

    public AuthResponse loginWithSocial(SocialLoginRequest request, String ipAddress) {
        SocialUserInfo socialInfo = switch (request.getProvider()) {
            case GOOGLE -> verifyGoogleToken(request.getToken());
            default -> throw new IllegalArgumentException("Unexpected value: " + request.getProvider());
        };

        // Try to find existing account linked to this social provider
        Optional<User> existingUser = userRepository.findBySocialAccount(request.getProvider(),
                socialInfo.providerId());

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            validateUserCanLogin(user);
            return buildAuthResponse(user, ipAddress, request.getDeviceInfo());
        }

        User newUser = createUserFromSocial(socialInfo, request.getProvider());
        return buildAuthResponse(newUser, ipAddress, request.getDeviceInfo());
    }

    // -----------------------------------------------------------
    // Refresh Token
    // Client sends refresh token → get new access token + new refresh token
    // Old refresh token is revoked (rotation)
    // -----------------------------------------------------------

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress) {
        String tokenHash = hashToken(request.getRefreshToken());

        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (!refreshToken.isValid()) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        User user = refreshToken.getUser();
        validateUserCanLogin(user);

        // Revoke the old token (rotation — prevents replay attacks)
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        // Issue a brand new pair
        return buildAuthResponse(user, ipAddress, refreshToken.getDeviceInfo());
    }

    // -----------------------------------------------------------
    // Logout — revoke a specific refresh token
    // -----------------------------------------------------------

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);

        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
        // If token not found — treat as success (already logged out)
    }

    // -----------------------------------------------------------
    // Logout from all devices — revoke ALL refresh tokens for this user
    // -----------------------------------------------------------

    @Transactional
    public void logoutAllDevices(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllByUserId(userId, OffsetDateTime.now());
        log.info("Revoked {} refresh tokens for user {}", revoked, userId);
    }

    @Transactional
    public void softDeleteAuthUser(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getDeletedAt() != null) {
                log.info("Auth user {} already deleted, skipping", userId);
                return; // idempotent
            }
            socialAccountRepository.deleteAllByUserId(userId);
            user.setDeletedAt(OffsetDateTime.now());
            userRepository.save(user);

            // Kick khỏi tất cả thiết bị ngay lập tức
            refreshTokenRepository.revokeAllByUserId(userId, OffsetDateTime.now());
            log.info("Soft deleted auth user {} and revoked all tokens", userId);
        });
    }

    @Transactional
    public void activateAuthUser(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getDeletedAt() != null) {
                log.warn("Cannot activate deleted auth user {}", userId);
                return;
            }
            if (user.getStatus() == User.UserStatus.ACTIVE) {
                log.info("Auth user {} already ACTIVE, skipping", userId);
                return;
            }
            user.setStatus(User.UserStatus.ACTIVE);
            userRepository.save(user);
            log.info("Activated auth user {} after user profile initialization success", userId);
        });
    }

    @Transactional
    public void restoreDeletedUser(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            if (user.getDeletedAt() == null) {
                log.info("Auth user {} not deleted, skipping restore", userId);
                return;
            }
            user.setDeletedAt(null);
            userRepository.save(user);
            log.info("Restored auth user {} after deletion saga failed", userId);
        });
    }

    // -----------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------

    private void validateUserCanLogin(User user) {
        if (user.getDeletedAt() != null) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        if (user.getStatus() == User.UserStatus.BLOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_BLOCKED);
        }
        if (user.getStatus() == User.UserStatus.UNVERIFIED) {
            throw new AppException(ErrorCode.ACCOUNT_NOT_VERIFIED);
        }
    }

    // Build the full AuthResponse: access token + refresh token + user info
    private AuthResponse buildAuthResponse(User user, String ipAddress, String deviceInfo) {
        String accessToken = jwtService.generateAccessToken(user);

        // Generate a cryptographically random refresh token (not a JWT — opaque token)
        // Opaque token: attacker cannot extract info from it even if they get it
        String rawRefreshToken = generateOpaqueToken();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .expiresAt(OffsetDateTime.now().plusSeconds(refreshTokenExpirationSeconds))
                .build();

        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .accessTokenExpiresIn(jwtService.getAccessTokenExpirationSeconds())
                .user(AuthResponse.UserInfo.builder()
                        .id(String.valueOf(user.getId()))
                        .fullName(user.getFullName())
                        .phone(user.getPhone())
                        .email(user.getEmail())
                        .avatarUrl(user.getAvatarUrl())
                        .role(user.getRole().name())
                        .build())
                .build();
    }

    private User createUserFromSocial(SocialUserInfo socialInfo, SocialAccount.Provider provider) {
        User draftUser = User.builder()
                .email(socialInfo.email())
                .fullName(socialInfo.name())
                .avatarUrl(socialInfo.avatarUrl())
                .status(User.UserStatus.ACTIVE)
                .role(User.Role.FARMER)
                .build();

        SocialAccount socialAccount = SocialAccount.builder()
                .user(draftUser)
                .provider(provider)
                .providerId(socialInfo.providerId())
                .email(socialInfo.email())
                .build();

        draftUser.getSocialAccounts().add(socialAccount);
        return registrationOrchestrationService.orchestrate(draftUser);
    }

    // -----------------------------------------------------------
    // Google token verification
    // The mobile app calls Google Sign-In SDK → gets an ID Token
    // We verify that token with Google's public keys
    // -----------------------------------------------------------

    private SocialUserInfo verifyGoogleToken(String idTokenString) {
        try {
            GoogleIdToken idToken = googleVerifier.verify(idTokenString);
            if (idToken == null) {
                throw new AppException(ErrorCode.SOCIAL_TOKEN_INVALID);
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            return new SocialUserInfo(
                    payload.getSubject(), // Google user ID
                    payload.getEmail(),
                    (String) payload.get("name"),
                    (String) payload.get("picture"));

        } catch (GeneralSecurityException | IOException e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw new AppException(ErrorCode.SOCIAL_TOKEN_INVALID);
        }
    }

    // Generate a 256-bit random opaque token encoded as hex
    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    // SHA-256 hash of a raw token string for DB storage
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // Internal record to carry social user info between methods
    private record SocialUserInfo(
            String providerId,
            String email,
            String name,
            String avatarUrl) {
    }

    public String generateGuestToken() {
        return jwtService.generateGuestToken();
    }

}