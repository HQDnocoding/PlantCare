package com.backend.scan_service.service;

import com.backend.scan_service.entity.IdempotencyRecord;
import com.backend.scan_service.repository.IdempotencyRecordRepository;
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
 * Service for handling HTTP-level request idempotency (Scan Service).
 *
 * Persists idempotency records in DB to ensure retried requests
 * return the same result without re-processing (duplicate uploads, etc).
 *
 * Strategy:
 * 1. Check if idempotency key already exists and not expired
 * 2. If exists and request body matches: return cached response
 * 3. If exists and request body differs: return 422 Unprocessable Entity
 * 4. If not exists: allow operation to proceed, then cache the response
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HttpIdempotencyService {

    private final IdempotencyRecordRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    /**
     * Check if a request has been processed before with this idempotency key
     * Scopes key by method + path to prevent collision (e.g., /scan vs /upload)
     *
     * @return Optional containing cached response if exists
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> getExistingRecord(String idempotencyKey, UUID userId, String method,
            String path) {
        Optional<IdempotencyRecord> record = idempotencyRepository.findByIdempotencyKeyAndUserIdAndMethodAndPath(
                idempotencyKey, userId, method, path);

        if (record.isPresent()) {
            IdempotencyRecord existing = record.get();

            // Check if record has expired
            if (existing.getExpiresAt().isBefore(Instant.now())) {
                log.debug("Idempotency record expired for key: {}", idempotencyKey);
                return Optional.empty();
            }

            log.info("Found existing idempotency record for key: {} (user: {})", idempotencyKey, userId);
            return record;
        }

        return Optional.empty();
    }

    /**
     * Store a processed request and its response for idempotency
     */
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
            // Don't throw - idempotency recording failure shouldn't fail the main operation
        }
    }

    /**
     * Validate that retried request has the same body as original
     *
     * @return true if request appears to be same request, false if conflict
     */
    public boolean validateRequestConsistency(IdempotencyRecord existing, String currentRequestBody) {
        if (existing.getRequestHash() == null) {
            // No hash stored, can't validate
            return true;
        }

        if (currentRequestBody == null) {
            // Current request has no body but original did
            return false;
        }

        String currentHash = computeHash(currentRequestBody);
        boolean matches = existing.getRequestHash().equals(currentHash);

        if (!matches) {
            log.warn("Request body mismatch for idempotency key: {} (conflict)", existing.getIdempotencyKey());
        }

        return matches;
    }

    /**
     * Generate idempotency key from request components (fallback if no header
     * provided)
     */
    public String generateIdempotencyKey(UUID userId, String method, String path) {
        return String.format("%s:%s:%s:%d", userId, method, path, System.currentTimeMillis() / 1000);
    }

    /**
     * Cleanup expired idempotency records (should be scheduled periodically)
     */
    @Transactional
    public int cleanupExpiredRecords() {
        int deleted = idempotencyRepository.deleteExpiredRecords(Instant.now());
        log.info("Cleaned up {} expired idempotency records", deleted);
        return deleted;
    }

    /**
     * Compute SHA-256 hash of request body for conflict detection
     */
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
            log.error("Failed to compute hash", e);
            throw new RuntimeException("Failed to compute request hash", e);
        }
    }
}
