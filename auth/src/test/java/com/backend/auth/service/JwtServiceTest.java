package com.backend.auth.service;

import com.backend.auth.domain.entity.OtpCode;
import com.backend.auth.domain.entity.User;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

// Pure unit test — no Spring context, no mocks, runs in milliseconds.
// JwtService has no external dependencies so we instantiate it directly.
@Slf4j
class JwtServiceTest {

    private JwtService jwtService;

    // Test secret — must be long enough for HMAC-SHA256 (min 256 bit = 32 chars)
    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-must-be-long-enough!!";
    private static final long ACCESS_TOKEN_EXPIRY = 900L; // 15 minutes

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, ACCESS_TOKEN_EXPIRY);
    }

    // Helper: build a minimal User object for testing
    private User buildUser(User.Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .fullName("Nguyen Van A")
                .phone("+84901234567")
                .role(role)
                .status(User.UserStatus.ACTIVE)
                .build();
    }

    // -----------------------------------------------------------
    @Nested
    @DisplayName("Access token")
    class AccessTokenTests {

        @Test
        @DisplayName("Generate token and extract userId — should match original user")
        void generateAndExtract_userId() {
            User user = buildUser(User.Role.FARMER);

            String token = jwtService.generateAccessToken(user);
            UUID extractedId = jwtService.extractUserId(token);

            assertThat(extractedId).isEqualTo(user.getId());
        }

        // @Test
        // @DisplayName("Extract role from token — should match original role")
        // void generateAndExtract_role() {
        // User user = buildUser(User.Role.ADMIN);

        // String token = jwtService.generateAccessToken(user);
        // String role = jwtService.extractRole(token);

        // assertThat(role).isEqualTo("ADMIN");
        // }

        @Test
        @DisplayName("Token signed with different secret — should throw ACCESS_TOKEN_INVALID")
        void token_wrongSecret_throwsException() {
            // Token created with a different JwtService (different secret)
            JwtService otherService = new JwtService(
                    "completely-different-secret-key-also-long-enough!!", 900L);
            User user = buildUser(User.Role.FARMER);
            String tokenFromOtherService = otherService.generateAccessToken(user);

            // Verifying with our service should fail
            assertThatThrownBy(() -> jwtService.extractUserId(tokenFromOtherService))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID);
        }

        @Test
        @DisplayName("Expired token — should throw ACCESS_TOKEN_INVALID")
        void expiredToken_throwsException() {
            // Create a service with -1 second expiry → token is already expired on creation
            JwtService shortLivedService = new JwtService(TEST_SECRET, -1L);
            User user = buildUser(User.Role.FARMER);
            String expiredToken = shortLivedService.generateAccessToken(user);

            assertThatThrownBy(() -> jwtService.extractUserId(expiredToken))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID);
        }

        @Test
        @DisplayName("Malformed token string — should throw ACCESS_TOKEN_INVALID")
        void malformedToken_throwsException() {
            assertThatThrownBy(() -> jwtService.extractUserId("this.is.not.a.jwt"))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID);
        }

        @Test
        @DisplayName("OTP verification token used as access token — should throw ACCESS_TOKEN_INVALID")
        void otpToken_usedAsAccessToken_throwsException() {
            // Type mismatch check — prevents cross-use of tokens
            String otpToken = jwtService.generateOtpVerificationToken(
                    "+84901234567", OtpCode.Purpose.REGISTER);

            assertThatThrownBy(() -> jwtService.extractUserId(otpToken))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_TOKEN_INVALID);
        }
    }

    // -----------------------------------------------------------
    @Nested
    @DisplayName("OTP verification token")
    class OtpVerificationTokenTests {

        @Test
        @DisplayName("Generate and extract phone — should match original phone")
        void generateAndExtract_phone() {
            String phone = "+84901234567";

            String token = jwtService.generateOtpVerificationToken(phone, OtpCode.Purpose.REGISTER);
            String extracted = jwtService.extractPhoneFromVerificationToken(
                    token, OtpCode.Purpose.REGISTER);

            assertThat(extracted).isEqualTo(phone);
        }

        @Test
        @DisplayName("Token for REGISTER used as LOGIN token — should throw OTP_VERIFICATION_TOKEN_INVALID")
        void wrongPurpose_throwsException() {
            String token = jwtService.generateOtpVerificationToken(
                    "+84901234567", OtpCode.Purpose.REGISTER);

            // Client tries to reuse REGISTER token for LOGIN — must be rejected
            assertThatThrownBy(() -> jwtService.extractPhoneFromVerificationToken(token, OtpCode.Purpose.LOGIN))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OTP_VERIFICATION_TOKEN_INVALID);
        }

        @Test
        @DisplayName("Access token used as OTP token — should throw OTP_VERIFICATION_TOKEN_INVALID")
        void accessToken_usedAsOtpToken_throwsException() {
            User user = buildUser(User.Role.FARMER);
            String accessToken = jwtService.generateAccessToken(user);
            assertThatThrownBy(
                    () -> jwtService.extractPhoneFromVerificationToken(accessToken, OtpCode.Purpose.REGISTER))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.OTP_VERIFICATION_TOKEN_INVALID);
        }
    }
}