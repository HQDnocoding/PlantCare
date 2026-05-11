package com.backend.auth.service;

import com.backend.auth.domain.entity.User;
import com.backend.auth.domain.entity.OtpCode;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;

    @Getter
    private final long accessTokenExpirationSeconds;

    private static final String TYPE_ACCESS = "ACCESS";
    private static final String TYPE_OTP = "OTP_VERIFICATION";
    private static final long OTP_TOKEN_EXPIRY_SECONDS = 300;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-seconds}") long accessTokenExpirationSeconds) {
        // Dùng raw UTF-8 để khớp với Python
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("type", TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpirationSeconds)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public UUID extractUserId(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, TYPE_ACCESS);
        return UUID.fromString(claims.getSubject());
    }

    public String generateOtpVerificationToken(String phone, OtpCode.Purpose purpose) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(phone)
                .claim("purpose", purpose.name())
                .claim("type", TYPE_OTP)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(OTP_TOKEN_EXPIRY_SECONDS)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String extractPhoneFromVerificationToken(String token, OtpCode.Purpose expectedPurpose) {
        try {
            Claims claims = parseClaims(token);
            validateTokenType(claims, TYPE_OTP);

            String purpose = claims.get("purpose", String.class);
            if (!expectedPurpose.name().equals(purpose)) {
                throw new AppException(ErrorCode.OTP_VERIFICATION_TOKEN_INVALID);
            }
            return claims.getSubject();
        } catch (AppException e) {
            throw new AppException(ErrorCode.OTP_VERIFICATION_TOKEN_INVALID);
        }
    }

    public String generateGuestToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .claim("role", "GUEST")
                .claim("type", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT Token expired: {}", e.getMessage());
            throw new AppException(ErrorCode.ACCESS_TOKEN_INVALID);
        } catch (JwtException e) {
            log.error("Invalid JWT Token: {}", e.getMessage());
            throw new AppException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
    }

    private void validateTokenType(Claims claims, String expectedType) {
        String type = claims.get("type", String.class);
        if (!expectedType.equals(type)) {
            throw new AppException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
    }
}