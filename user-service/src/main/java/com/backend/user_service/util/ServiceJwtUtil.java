package com.backend.user_service.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class ServiceJwtUtil {

    private final SecretKey secretKey;
    private final String serviceName;

    private static final long TOKEN_EXPIRY_MS = 300_000;
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_SERVICE = "service";
    private static final String SERVICE_TOKEN_TYPE = "service-to-service";
    private static final String INTERNAL_ISSUER = "plant-microservices";

    public ServiceJwtUtil(
            @Value("${internal.secret}") String internalSecret,
            @Value("${spring.application.name}") String serviceName) {
        // Dùng raw UTF-8 để khớp với Python
        byte[] keyBytes = internalSecret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.serviceName = serviceName;
    }

    public String generateServiceToken() {
        try {
            long now = System.currentTimeMillis();

            return Jwts.builder()
                    .subject(serviceName)
                    .claim(CLAIM_TYPE, SERVICE_TOKEN_TYPE)
                    .claim(CLAIM_SERVICE, serviceName)
                    .issuer(INTERNAL_ISSUER)
                    .issuedAt(new Date(now))
                    .expiration(new Date(now + TOKEN_EXPIRY_MS))
                    .signWith(secretKey, Jwts.SIG.HS256)
                    .compact();
        } catch (Exception e) {
            log.error("Failed to generate service-to-service JWT for: {}", serviceName, e);
            throw new RuntimeException("Internal token generation failed", e);
        }
    }

    public String validateServiceToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            Claims claims = parseClaims(token);

            String type = claims.get(CLAIM_TYPE, String.class);
            if (!SERVICE_TOKEN_TYPE.equals(type)) {
                log.warn("Invalid internal token type detected: {}", type);
                return null;
            }

            String sourceService = claims.get(CLAIM_SERVICE, String.class);
            log.debug("Internal token validated for service: {}", sourceService);
            return sourceService;

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Internal service token validation failed: {}", e.getMessage());
            return null;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}