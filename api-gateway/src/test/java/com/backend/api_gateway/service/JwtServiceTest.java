package com.backend.api_gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.api_gateway.exception.AppException;
import com.backend.api_gateway.exception.ErrorCode;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "test-secret-key-for-jwt-validation-purposes-only";
    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret);
    }

    @Nested
    @DisplayName("extractUserId")
    class ExtractUserIdTests {

        @Test
        @DisplayName("Should extract userId from valid ACCESS token")
        void extractUserId_validAccessToken() {
            // Given
            String token = createTestToken(testUserId.toString(), "USER", "ACCESS");

            // When
            UUID result = jwtService.extractUserId(token);

            // Then
            assertThat(result).isEqualTo(testUserId);
        }

        @Test
        @DisplayName("Should throw ACCESS_TOKEN_INVALID for REFRESH token")
        void extractUserId_refreshToken() {
            // Given
            String token = createTestToken(testUserId.toString(), "USER", "REFRESH");

            // When & Then
            assertThatThrownBy(() -> jwtService.extractUserId(token))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_TOKEN_INVALID);
        }

        @Test
        @DisplayName("Should throw ACCESS_TOKEN_INVALID for expired token")
        void extractUserId_expiredToken() {
            // Given - Create expired token
            String expiredToken = Jwts.builder()
                    .subject(testUserId.toString())
                    .claim("role", "USER")
                    .claim("type", "ACCESS")
                    .issuedAt(java.util.Date.from(java.time.Instant.now().minusSeconds(3600)))
                    .expiration(java.util.Date.from(java.time.Instant.now().minusSeconds(1800))) // Expired 30 mins ago
                    .signWith(Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .compact();

            // When & Then
            assertThatThrownBy(() -> jwtService.extractUserId(expiredToken))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_TOKEN_INVALID);
        }

        @Test
        @DisplayName("Should throw ACCESS_TOKEN_INVALID for malformed token")
        void extractUserId_malformedToken() {
            // Given
            String malformedToken = "invalid.jwt.token";

            // When & Then
            assertThatThrownBy(() -> jwtService.extractUserId(malformedToken))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_TOKEN_INVALID);
        }
    }

    @Nested
    @DisplayName("extractRole")
    class ExtractRoleTests {

        @Test
        @DisplayName("Should extract role from valid token")
        void extractRole_validToken() {
            // Given
            String token = createTestToken(testUserId.toString(), "USER", "ACCESS");

            // When
            String result = jwtService.extractRole(token);

            // Then
            assertThat(result).isEqualTo("USER");
        }

        @Test
        @DisplayName("Should return empty string when role claim is missing")
        void extractRole_missingRole() {
            // Given - Token without role claim
            String token = Jwts.builder()
                    .subject(testUserId.toString())
                    .claim("type", "ACCESS")
                    .signWith(Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .compact();

            // When
            String result = jwtService.extractRole(token);

            // Then
            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("Should throw ACCESS_TOKEN_INVALID for invalid token")
        void extractRole_invalidToken() {
            // Given
            String invalidToken = "invalid.token.here";

            // When & Then
            assertThatThrownBy(() -> jwtService.extractRole(invalidToken))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_TOKEN_INVALID);
        }
    }

    private String createTestToken(String subject, String role, String type) {
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(java.util.Date.from(java.time.Instant.now()))
                .expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(3600))) // Valid for 1 hour
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
    }
}