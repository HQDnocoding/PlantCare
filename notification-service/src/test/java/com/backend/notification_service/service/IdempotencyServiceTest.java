package com.backend.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService Tests")
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private final String serviceId = "notification-service";
    private final String messageId = "test-message-123";

    @Nested
    @DisplayName("isProcessed")
    class IsProcessedTests {

        @Test
        @DisplayName("Should return true when message already processed")
        void isProcessed_alreadyProcessed() {
            // Given
            String key = "idempotency:notification-service:test-message-123";
            when(redisTemplate.hasKey(key)).thenReturn(true);

            // When
            boolean result = idempotencyService.isProcessed(serviceId, messageId);

            // Then
            assertThat(result).isTrue();
            verify(redisTemplate).hasKey(key);
        }

        @Test
        @DisplayName("Should return false when message not processed")
        void isProcessed_notProcessed() {
            // Given
            String key = "idempotency:notification-service:test-message-123";
            when(redisTemplate.hasKey(key)).thenReturn(false);

            // When
            boolean result = idempotencyService.isProcessed(serviceId, messageId);

            // Then
            assertThat(result).isFalse();
            verify(redisTemplate).hasKey(key);
        }

        @Test
        @DisplayName("Should return false when hasKey returns null")
        void isProcessed_nullResult() {
            // Given
            String key = "idempotency:notification-service:test-message-123";
            when(redisTemplate.hasKey(key)).thenReturn(null);

            // When
            boolean result = idempotencyService.isProcessed(serviceId, messageId);

            // Then
            assertThat(result).isFalse();
            verify(redisTemplate).hasKey(key);
        }
    }

    @Nested
    @DisplayName("markAsProcessed")
    class MarkAsProcessedTests {

        @Test
        @DisplayName("Should mark message as processed with TTL")
        void markAsProcessed_success() {
            // Given
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // When
            idempotencyService.markAsProcessed(serviceId, messageId);

            // Then
            verify(redisTemplate).opsForValue();
            verify(valueOperations).set(eq("idempotency:notification-service:test-message-123"), eq("processed"),
                    eq(86400L), eq(TimeUnit.SECONDS));
        }
    }
}