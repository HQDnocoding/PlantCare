package com.backend.auth.consumer;

import com.backend.auth.event.UserDeletedEvent;
import com.backend.auth.service.AuthService;
import com.backend.auth.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for user deletion events.
 * Soft-deletes auth records when users are deleted from system.
 *
 * Idempotency Strategy:
 * - Uses Kafka partition + offset as unique message identifier
 * - Prevents duplicate processing if consumer restarts or retries
 * - Coordinates with IdempotencyService (Redis-backed)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final AuthService authService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * Listen for user.deleted events from User Service.
     * Soft-deletes corresponding auth records to maintain consistency.
     *
     * Idempotency:
     * - Each Kafka message has unique partition:offset
     * - Combined with userId and event type to create unique messageId
     * - Prevents duplicate processing if consumer retries
     */
    @KafkaListener(topics = "user.deleted", groupId = "auth-service")
    public void onUserDeleted(
            @Payload String eventJson,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        try {
            UserDeletedEvent event = objectMapper.readValue(eventJson, UserDeletedEvent.class);
            String userId = event.getUserId();

            // Use Kafka partition + offset as unique identifier
            String messageId = userId + "-deleted-" + partition + "-" + offset;

            // Compute payload hash for conflict detection
            String payloadHash = idempotencyService.computeHash(eventJson);

            // Check if already processed (with payload validation)
            if (idempotencyService.isProcessed("auth-service", messageId, payloadHash)) {
                log.debug("User {} already soft-deleted (partition={}, offset={}), skipping",
                        userId, partition, offset);
                return;
            }

            // Perform soft delete
            authService.softDeleteAuthUser(UUID.fromString(userId));

            // Mark as processed with payload hash
            idempotencyService.markAsProcessed("auth-service", messageId, payloadHash);
            log.info("Successfully soft-deleted auth user {} (partition={}, offset={})",
                    userId, partition, offset);

        } catch (IllegalStateException e) {
            // Payload conflict - poison pill
            log.error("Payload conflict detected for user.deleted message | partition={} offset={} | {}",
                    partition, offset, e.getMessage());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Poison pill - skip message to unblock partition
            log.error("Malformed user.deleted payload | partition={} offset={} error={}",
                    partition, offset, e.getMessage());

        } catch (Exception e) {
            // Re-throw for Kafka to retry
            log.error("Failed to process user.deleted message | partition={} offset={} error={}",
                    partition, offset, e.getMessage(), e);
            throw new RuntimeException("Failed to process user.deleted", e);
        }
    }
}
