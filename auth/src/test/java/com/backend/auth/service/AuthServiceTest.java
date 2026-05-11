package com.backend.auth.service;

import com.backend.auth.client.UserServiceClient;
import com.backend.auth.domain.dto.request.PhoneLoginRequest;
import com.backend.auth.domain.dto.request.RefreshTokenRequest;
import com.backend.auth.domain.dto.response.AuthResponse;
import com.backend.auth.domain.entity.RefreshToken;
import com.backend.auth.domain.entity.User;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import com.backend.auth.repository.OutboxRepository;
import com.backend.auth.repository.RefreshTokenRepository;
import com.backend.auth.repository.SocialAccountRepository;
import com.backend.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationSeconds", 3600L);
    }

    @Nested
    @DisplayName("Phone login")
    class PhoneLoginTests {

        @Test
        @DisplayName("Valid credentials should return auth response")
        void loginWithPhone_validCredentials_returnsAuthResponse() {
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .phone("+84901234567")
                    .passwordHash("encoded-password")
                    .fullName("Test User")
                    .status(User.UserStatus.ACTIVE)
                    .role(User.Role.FARMER)
                    .build();

            PhoneLoginRequest request = new PhoneLoginRequest();
            request.setPhone("+84901234567");
            request.setPassword("password");
            request.setDeviceInfo("Android Test");

            when(userRepository.findByPhoneAndDeletedAtIsNull("+84901234567"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password", "encoded-password")).thenReturn(true);
            when(jwtService.generateAccessToken(user)).thenReturn("access-token");
            when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AuthResponse response = authService.loginWithPhone(request, "127.0.0.1");

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isNotBlank();
            assertThat(response.getAccessTokenExpiresIn()).isEqualTo(900L);
            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getPhone()).isEqualTo(user.getPhone());
            assertThat(response.getUser().getFullName()).isEqualTo("Test User");

            verify(refreshTokenRepository, times(1)).save(refreshTokenCaptor.capture());
            RefreshToken savedToken = refreshTokenCaptor.getValue();
            assertThat(savedToken.getUser()).isEqualTo(user);
            assertThat(savedToken.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(savedToken.getDeviceInfo()).isEqualTo("Android Test");
            assertThat(savedToken.getExpiresAt()).isAfter(OffsetDateTime.now());
        }

        @Test
        @DisplayName("Wrong password should throw INVALID_CREDENTIALS")
        void loginWithPhone_wrongPassword_throwsInvalidCredentials() {
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .phone("+84901234567")
                    .passwordHash("encoded-password")
                    .fullName("Test User")
                    .status(User.UserStatus.ACTIVE)
                    .role(User.Role.FARMER)
                    .build();

            PhoneLoginRequest request = new PhoneLoginRequest();
            request.setPhone("+84901234567");
            request.setPassword("wrong-password");

            when(userRepository.findByPhoneAndDeletedAtIsNull("+84901234567"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.loginWithPhone(request, "127.0.0.1"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }
    }

    @Nested
    @DisplayName("Refresh token")
    class RefreshTokenTests {

        @Test
        @DisplayName("Valid refresh token should rotate and return auth response")
        void refreshToken_validToken_rotatesAndReturnsResponse() {
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .phone("+84901234567")
                    .fullName("Refresher")
                    .status(User.UserStatus.ACTIVE)
                    .role(User.Role.FARMER)
                    .build();

            RefreshToken refreshToken = RefreshToken.builder()
                    .user(user)
                    .tokenHash("existing-hash")
                    .deviceInfo("Web Client")
                    .ipAddress("127.0.0.1")
                    .expiresAt(OffsetDateTime.now().plusSeconds(3600))
                    .build();

            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(refreshToken));
            when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
            when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("raw-refresh-token");

            AuthResponse response = authService.refreshToken(request, "127.0.0.1");

            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isNotBlank();
            assertThat(response.getUser().getPhone()).isEqualTo(user.getPhone());
            assertThat(refreshToken.isRevoked()).isTrue();

            verify(refreshTokenRepository, times(1)).save(refreshToken);
        }

        @Test
        @DisplayName("Expired or revoked refresh token should throw REFRESH_TOKEN_INVALID")
        void refreshToken_invalidToken_throwsException() {
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .phone("+84901234567")
                    .status(User.UserStatus.ACTIVE)
                    .role(User.Role.FARMER)
                    .build();

            RefreshToken refreshToken = RefreshToken.builder()
                    .user(user)
                    .tokenHash("existing-hash")
                    .deviceInfo("Web Client")
                    .ipAddress("127.0.0.1")
                    .expiresAt(OffsetDateTime.now().minusSeconds(1))
                    .build();

            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(refreshToken));

            RefreshTokenRequest request = new RefreshTokenRequest();
            request.setRefreshToken("raw-refresh-token");

            assertThatThrownBy(() -> authService.refreshToken(request, "127.0.0.1"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);

            verify(refreshTokenRepository, never()).save(refreshToken);
        }
    }

    @Nested
    @DisplayName("Logout and user cleanup")
    class LogoutTests {

        @Test
        @DisplayName("Logout with a known token should revoke it")
        void logout_knownToken_revokesToken() {
            RefreshToken token = RefreshToken.builder()
                    .tokenHash("existing-hash")
                    .expiresAt(OffsetDateTime.now().plusSeconds(3600))
                    .build();

            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.of(token));
            when(refreshTokenRepository.save(token)).thenReturn(token);

            authService.logout("raw-refresh-token");

            assertThat(token.isRevoked()).isTrue();
            verify(refreshTokenRepository, times(1)).save(token);
        }

        @Test
        @DisplayName("Logout with unknown token should succeed silently")
        void logout_unknownToken_succeeds() {
            when(refreshTokenRepository.findByTokenHash(anyString()))
                    .thenReturn(Optional.empty());

            authService.logout("raw-refresh-token");

            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("Logout all devices should revoke tokens for the user")
        void logoutAllDevices_revokesAllTokens() {
            UUID userId = UUID.randomUUID();
            when(refreshTokenRepository.revokeAllByUserId(any(UUID.class), any(OffsetDateTime.class)))
                    .thenReturn(2);

            authService.logoutAllDevices(userId);

            verify(refreshTokenRepository, times(1)).revokeAllByUserId(any(UUID.class), any(OffsetDateTime.class));
        }

        @Test
        @DisplayName("Soft delete should mark user deleted and revoke all refresh tokens")
        void softDeleteAuthUser_existingUser_revokesTokens() {
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .fullName("Delete Me")
                    .status(User.UserStatus.ACTIVE)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(refreshTokenRepository.revokeAllByUserId(any(UUID.class), any(OffsetDateTime.class))).thenReturn(1);

            authService.softDeleteAuthUser(userId);

            assertThat(user.getDeletedAt()).isNotNull();
            verify(socialAccountRepository, times(1)).deleteAllByUserId(userId);
            verify(userRepository, times(1)).save(user);
            verify(refreshTokenRepository, times(1)).revokeAllByUserId(eq(userId), any(OffsetDateTime.class));
        }
    }
}
