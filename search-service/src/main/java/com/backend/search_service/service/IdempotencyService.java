package com.backend.search_service.service;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:search:";
    private static final long IDEMPOTENCY_TTL_SECONDS = 300; // 5 minutes

    public boolean isProcessed(String serviceId, String messageId) {
        String key = buildKey(serviceId, messageId);
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }

    public void markAsProcessed(String serviceId, String messageId) {
        String key = buildKey(serviceId, messageId);
        redisTemplate.opsForValue().set(key, "processed", IDEMPOTENCY_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("Marked message {} as processed in service {}", messageId, serviceId);
    }

    private String buildKey(String serviceId, String messageId) {
        return IDEMPOTENCY_KEY_PREFIX + serviceId + ":" + messageId;
    }
}
