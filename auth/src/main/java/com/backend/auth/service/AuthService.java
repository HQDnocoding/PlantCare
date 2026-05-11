package com.backend.auth.service;

import com.backend.auth.client.UserServiceClient;
import com.backend.auth.client.dto.CreateProfileRequest;
import com.backend.auth.domain.dto.request.*;
import com.backend.auth.domain.dto.response.AuthResponse;
import com.backend.auth.domain.entity.*;
import com.backend.auth.entity.OutboxEvent;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import com.backend.auth.repository.RefreshTokenRepository;
import com.backend.auth.repository.UserRepository;
import com.backend.auth.repository.OutboxRepository;
import com.backend.auth.repository.SocialAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.backend.auth.util.PhoneUtil;
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
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxRepository outboxRepository;
    private final JwtService jwtService;
    private final UserServiceClient userServiceClient;
    // private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final SocialAccountRepository socialAccountRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.jwt.refresh-token-expiration-seconds}")
    private long refreshTokenExpirationSeconds;

    @Value("${app.oauth2.google.client-id}")
    private String googleClientId;

    @Value("${app.oauth2.facebook.app-id}")
    private String facebookAppId;

    @Value("${app.oauth2.facebook.app-secret}")
    private String facebookAppSecret;

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

    /**
     * Create user profile (synchronous with retry).
     * Directly call User Service to create profile immediately.
     * If fails, retry up to 3 times with exponential backoff before giving up.
     */
    private void createUserProfile(UUID userId, String displayName, String avatarUrl) {
        CreateProfileRequest request = CreateProfileRequest.builder()
                .userId(userId)
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .build();

        int maxRetries = 3;
        long initialDelayMs = 100;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                userServiceClient.createProfile(request);
                log.info("User profile created successfully: userId={} displayName={}",
                        userId, displayName);
                return;
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("Failed to create user profile after {} retries: userId={}",
                            maxRetries, userId, e);
                    throw new RuntimeException("Failed to create profile after " + maxRetries + " retries", e);
                }

                long delayMs = initialDelayMs * (long) Math.pow(2, attempt - 1);
                log.warn("Attempt {}/{} to create user profile failed, retrying in {}ms: userId={}",
                        attempt, maxRetries, delayMs, userId);

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Profile creation interrupted", ie);
                }
            }
        }
    }

    // -----------------------------------------------------------
    // Phone Registration
    // Flow: sendOtp → verifyOtp (get verificationToken) → register
    // -----------------------------------------------------------

    @Transactional
    public AuthResponse registerWithPhone(PhoneRegisterRequest request, String ipAddress) {
        String phone = PhoneUtil.normalize(request.getPhone());

        // Step 1: Verify the OTP verification token (proves phone was verified)
        String verifiedPhone = jwtService.extractPhoneFromVerificationToken(
                request.getOtpVerificationToken(),
                OtpCode.Purpose.REGISTER);

        // Token must be for the same phone being registered
        if (!verifiedPhone.equals(phone)) {
            throw new AppException(ErrorCode.OTP_VERIFICATION_TOKEN_INVALID);
        }

        // Step 2: Check phone is not already taken
        if (userRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new AppException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        // Step 3: Create the user
        User user = User.builder()
                .phone(phone)
                .fullName(request.getFullName())
                .passwordHash(
                        request.getPassword() != null
                                ? passwordEncoder.encode(request.getPassword())
                                : null)
                .status(User.UserStatus.ACTIVE) // Phone already verified via OTP
                .role(User.Role.FARMER)
                .build();

        userRepository.save(user);
        createUserProfile(user.getId(), user.getFullName(), null);

        return buildAuthResponse(user, ipAddress, null);
    }

    // -----------------------------------------------------------
    // Phone + Password Login
    // -----------------------------------------------------------

    @Transactional
    public AuthResponse loginWithPhone(PhoneLoginRequest request, String ipAddress) {
        String phone = PhoneUtil.normalize(request.getPhone());

        User user = userRepository.findByPhoneAndDeletedAtIsNull(phone)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));
        // Note: throw INVALID_CREDENTIALS, not ACCOUNT_NOT_FOUND
        // — do not reveal whether the phone exists or not

        validateUserCanLogin(user);

        if (!user.hasPassword()) {
            // User registered via social only — no password set
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        return buildAuthResponse(user, ipAddress, request.getDeviceInfo());
    }

    // -----------------------------------------------------------
    // OTP Login (no password — login via OTP directly)
    // Flow: sendOtp → verifyOtp (get verificationToken) → loginWithOtp
    // -----------------------------------------------------------

    @Transactional
    public AuthResponse loginWithOtp(PhoneRegisterRequest request, String ipAddress) {
        String phone = PhoneUtil.normalize(request.getPhone());

        String verifiedPhone = jwtService.extractPhoneFromVerificationToken(
                request.getOtpVerificationToken(),
                OtpCode.Purpose.LOGIN);

        if (!verifiedPhone.equals(phone)) {
            throw new AppException(ErrorCode.OTP_VERIFICATION_TOKEN_INVALID);
        }

        User user = userRepository.findByPhoneAndDeletedAtIsNull(phone)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        validateUserCanLogin(user);

        return buildAuthResponse(user, ipAddress, null);
    }

    // -----------------------------------------------------------
    // Social Login (Google / Facebook)
    // If account doesn't exist, auto-create it (first login = register)
    // -----------------------------------------------------------

    @Transactional
    public AuthResponse loginWithSocial(SocialLoginRequest request, String ipAddress) {
        SocialUserInfo socialInfo = switch (request.getProvider()) {
            case GOOGLE -> verifyGoogleToken(request.getToken());
            case FACEBOOK -> verifyFacebookToken(request.getToken());
            default -> throw new IllegalArgumentException("Unexpected value: " + request.getProvider());
        };

        // Try to find existing account linked to this social provider
        return userRepository.findBySocialAccount(request.getProvider(), socialInfo.providerId())
                .map(user -> {
                    validateUserCanLogin(user);
                    return buildAuthResponse(user, ipAddress, request.getDeviceInfo());
                })
                .orElseGet(() -> {
                    User newUser = createUserFromSocial(socialInfo, request.getProvider());
                    return buildAuthResponse(newUser, ipAddress, request.getDeviceInfo());
                });
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
        User user = User.builder()
                .email(socialInfo.email())
                .fullName(socialInfo.name())
                .avatarUrl(socialInfo.avatarUrl())
                .status(User.UserStatus.ACTIVE) // Social accounts are pre-verified by provider
                .role(User.Role.FARMER)
                .build();

        SocialAccount socialAccount = SocialAccount.builder()
                .user(user)
                .provider(provider)
                .providerId(socialInfo.providerId())
                .email(socialInfo.email())
                .build();

        user.getSocialAccounts().add(socialAccount);
        User saved = userRepository.save(user);
        createUserProfile(saved.getId(), saved.getFullName(), saved.getAvatarUrl());
        return saved;
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

    // -----------------------------------------------------------
    // Facebook token verification
    // Call Facebook Graph API to verify the access token
    // -----------------------------------------------------------

    private SocialUserInfo verifyFacebookToken(String accessToken) {
        try {
            // Step 1: Verify token is valid and belongs to our app
            String appToken = facebookAppId + "|" + facebookAppSecret;
            String debugUrl = "https://graph.facebook.com/debug_token"
                    + "?input_token=" + accessToken
                    + "&access_token=" + appToken;

            Map<?, ?> debugResponse = restTemplate.getForObject(debugUrl, Map.class);

            // Fix Bug #10: null-check — Facebook API có thể trả null hoặc format không mong
            // muốn
            if (debugResponse == null || !debugResponse.containsKey("data")) {
                throw new AppException(ErrorCode.SOCIAL_TOKEN_INVALID);
            }
            Map<?, ?> data = (Map<?, ?>) debugResponse.get("data");
            if (data == null || !Boolean.TRUE.equals(data.get("is_valid"))) {
                throw new AppException(ErrorCode.SOCIAL_TOKEN_INVALID);
            }

            // Step 2: Fetch user profile
            String profileUrl = "https://graph.facebook.com/me"
                    + "?fields=id,name,email,picture"
                    + "&access_token=" + accessToken;

            Map<?, ?> profile = restTemplate.getForObject(profileUrl, Map.class);

            // Fix Bug #10: null-check profile
            if (profile == null || profile.get("id") == null) {
                throw new AppException(ErrorCode.SOCIAL_TOKEN_INVALID);
            }

            // Fix Bug #10: picture là optional — user có thể không có avatar
            String avatarUrl = null;
            Object pictureObj = profile.get("picture");
            if (pictureObj instanceof Map<?, ?> pictureWrapper) {
                Object pictureDataObj = pictureWrapper.get("data");
                if (pictureDataObj instanceof Map<?, ?> pictureData) {
                    avatarUrl = (String) pictureData.get("url");
                }
            }

            // Fix Bug #10: email có thể null (Facebook không bắt buộc cấp email)
            String email = (String) profile.get("email");
            String name = (String) profile.get("name");

            return new SocialUserInfo(
                    (String) profile.get("id"),
                    email, // có thể null — xử lý ở createUserFromSocial
                    name,
                    avatarUrl);

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Facebook token verification failed: {}", e.getMessage());
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

    /**
     * Delete user by Facebook ID (triggered by Facebook data deletion callback)
     * Cascades delete: user, auth tokens, refresh tokens
     */
    @Transactional
    public void deleteUserByFacebookId(String facebookUserId) {
        log.info("Deleting user with Facebook ID: {}", facebookUserId);

        try {
            // Find user by Facebook ID via social account
            var userOptional = userRepository.findBySocialAccount(
                    SocialAccount.Provider.FACEBOOK,
                    facebookUserId);

            if (userOptional.isEmpty()) {
                log.warn("User not found with Facebook ID: {}", facebookUserId);
                return;
            }

            User user = userOptional.get();
            UUID userId = user.getId();

            // Delete user (cascade delete will handle related entities:
            // SocialAccounts, RefreshTokens, etc.)
            userRepository.delete(user);
            log.info("Successfully deleted user: {} by Facebook request", userId);

        } catch (Exception e) {
            log.error("Failed to delete user with Facebook ID: {}", facebookUserId, e);
            throw new RuntimeException("Failed to process deletion request", e);
        }
    }
}