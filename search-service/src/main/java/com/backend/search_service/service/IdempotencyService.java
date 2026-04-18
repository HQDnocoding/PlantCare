package com.backend.search_service.service;

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

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:search:";
    private static final long IDEMPOTENCY_TTL_SECONDS = 86400; // 24 hours

    /**
     * Check if message ID already processed
     * 
     * @param serviceId Service name (e.g., "search-service")
     * @param messageId Unique message ID (usually from event header or ID)
     * @return true if already processed (skip), false if new (process)
     */
    public boolean isProcessed(String serviceId, String messageId) {
        String key = buildKey(serviceId, messageId);
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }

    /**
     * Mark message as processed
     * 
     * @param serviceId Service name
     * @param messageId Unique message ID
     */
    public void markAsProcessed(String serviceId, String messageId) {
        String key = buildKey(serviceId, messageId);
        redisTemplate.opsForValue().set(key, "processed", IDEMPOTENCY_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("Marked message {} as processed in service {}", messageId, serviceId);
    }

    /**
     * Build Redis key from service name and message ID
     */
    private String buildKey(String serviceId, String messageId) {
        return IDEMPOTENCY_KEY_PREFIX + serviceId + ":" + messageId;
    }
}
