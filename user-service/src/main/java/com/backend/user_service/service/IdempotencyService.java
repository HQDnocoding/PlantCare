package com.backend.user_service.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Idempotency service - prevent duplicate processing of Kafka messages
 * Each consumer tracks processed message IDs in Redis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final long IDEMPOTENCY_TTL_SECONDS = 86400; // 24 hours

    /**
     * Check if message ID already processed (with payload hash validation)
     * 
     * @param serviceId   Service name
     * @param messageId   Unique message ID (usually from event header)
     * @param payloadHash SHA-256 hash of payload for conflict detection
     * @return true if already processed (skip), false if new (process)
     * @throws IllegalStateException if message exists but payload hash differs
     *                               (conflict detected)
     */
    public boolean isProcessed(String serviceId, String messageId, String payloadHash) {
        String key = buildKey(serviceId, messageId);
        String storedHash = redisTemplate.opsForValue().get(key);

        if (storedHash == null) {
            return false; // Not processed yet
        }

        // Message already processed - validate payload consistency
        if (!storedHash.equals(payloadHash)) {
            log.warn("Payload hash mismatch for message {} in service {} - conflict detected",
                    messageId, serviceId);
            throw new IllegalStateException("Payload conflict for message: " + messageId);
        }

        return true; // Already processed with same payload
    }

    /**
     * Check if message ID already processed (without validation - legacy)
     * 
     * @param serviceId Service name (e.g., "user-service")
     * @param messageId Unique message ID (usually from event header)
     * @return true if already processed (skip), false if new (process)
     */
    public boolean isProcessed(String serviceId, String messageId) {
        String key = buildKey(serviceId, messageId);
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }

    /**
     * Mark message as processed (with payload hash for conflict detection)
     * 
     * @param serviceId   Service name
     * @param messageId   Unique message ID
     * @param payloadHash SHA-256 hash of payload
     */
    public void markAsProcessed(String serviceId, String messageId, String payloadHash) {
        String key = buildKey(serviceId, messageId);
        redisTemplate.opsForValue().set(key, payloadHash, IDEMPOTENCY_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("Marked message {} as processed in service {} with hash {}",
                messageId, serviceId, payloadHash.substring(0, 8) + "...");
    }

    /**
     * Mark message as processed (legacy - without hash)
     * 
     * @param serviceId Service name
     * @param messageId Unique message ID
     */
    public void markAsProcessed(String serviceId, String messageId) {
        String key = buildKey(serviceId, messageId);
        redisTemplate.opsForValue().set(key, "processed", IDEMPOTENCY_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("Marked message {} as processed in service {}", messageId, serviceId);
    }

    private String buildKey(String serviceId, String messageId) {
        return IDEMPOTENCY_KEY_PREFIX + serviceId + ":" + messageId;
    }

    /**
     * Compute SHA-256 hash of message payload
     */
    public String computeHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));

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
            throw new RuntimeException("Failed to compute message hash", e);
        }
    }
}
