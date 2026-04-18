package com.backend.user_service.service;

import com.backend.user_service.entity.IdempotencyRecord;
import com.backend.user_service.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for handling HTTP-level request idempotency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HttpIdempotencyService {

    private final IdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> getExistingRecord(String idempotencyKey, UUID userId, String method,
            String path) {
        Optional<IdempotencyRecord> record = idempotencyRepository.findByIdempotencyKeyAndUserIdAndMethodAndPath(
                idempotencyKey, userId, method, path);

        if (record.isPresent()) {
            IdempotencyRecord existing = record.get();

            if (existing.getExpiresAt().isBefore(Instant.now())) {
                log.debug("Idempotency record expired for key: {}", idempotencyKey);
                return Optional.empty();
            }

            log.info("Found existing idempotency record for key: {} (user: {}) | Method: {} Path: {}", idempotencyKey,
                    userId, method, path);
            return record;
        }

        return Optional.empty();
    }

    @Transactional
    public void recordProcessedRequest(String idempotencyKey, UUID userId, String method, String path,
            int responseStatus, String responseBody, long ttlSeconds, String requestBody) {
        try {
            String requestHash = requestBody != null ? computeHash(requestBody) : null;

            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .userId(userId)
                    .method(method)
                    .path(path)
                    .responseStatus(responseStatus)
                    .responseBody(responseBody)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(ttlSeconds))
                    .requestHash(requestHash)
                    .build();

            idempotencyRepository.save(record);
            log.info("Recorded idempotent request: key={}, user={}, status={}", idempotencyKey, userId, responseStatus);

        } catch (Exception e) {
            log.error("Failed to record idempotency: {}", idempotencyKey, e);
        }
    }

    public boolean validateRequestConsistency(IdempotencyRecord existing, String currentRequestBody) {
        if (existing.getRequestHash() == null) {
            return true;
        }

        if (currentRequestBody == null) {
            return false;
        }

        String currentHash = computeHash(currentRequestBody);
        boolean matches = existing.getRequestHash().equals(currentHash);

        if (!matches) {
            log.warn("Request body mismatch for idempotency key: {}", existing.getIdempotencyKey());
        }

        return matches;
    }

    public String generateIdempotencyKey(UUID userId, String method, String path) {
        return String.format("%s:%s:%s:%d", userId, method, path, System.currentTimeMillis() / 1000);
    }

    @Transactional
    public int cleanupExpiredRecords() {
        int deleted = idempotencyRepository.deleteExpiredRecords(Instant.now());
        log.info("Cleaned up {} expired idempotency records", deleted);
        return deleted;
    }

    private String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (Exception e) {
            log.error("Error computing request hash", e);
            return null;
        }
    }
}
