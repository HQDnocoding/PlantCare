package com.backend.api_gateway.service;

import com.backend.api_gateway.exception.AppException;
import com.backend.api_gateway.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;

    public JwtService(@Value("${jwt.secret}") String secret) {
        // Dùng raw UTF-8 để khớp với auth-service
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public UUID extractUserId(String token) {
        Claims claims = parseClaims(token);
        validateTokenType(claims, "ACCESS");
        return UUID.fromString(claims.getSubject());
    }

    public String extractRole(String token) {
        String role = parseClaims(token).get("role", String.class);
        if (role == null) {
            log.warn("Token has no role claim - defaulting to empty");
            return "";
        }
        return role;
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            throw new AppException(ErrorCode.ACCESS_TOKEN_INVALID);
        } catch (JwtException e) {
            log.debug("JWT invalid: {}", e.getMessage());
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