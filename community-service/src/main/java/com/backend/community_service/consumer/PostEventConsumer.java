package com.backend.community_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.backend.community_service.event.UserUpdatedEvent;
import com.backend.community_service.service.AuthorCacheService;
import com.backend.community_service.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka consumer for user events affecting community
 * Maintains author cache invalidation with idempotency
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostEventConsumer {

    private final IdempotencyService idempotencyService;
    private final AuthorCacheService authorCacheService;
    private final ObjectMapper objectMapper;

    /**
     * Listen for user.updated events
     * Invalidates author cache entry for efficiency
     */
    @KafkaListener(topics = "user.updated", groupId = "community-service")
    public void onUserUpdated(@Payload String eventJson,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        try {
            UserUpdatedEvent event = objectMapper.readValue(eventJson, UserUpdatedEvent.class);
            // Use Kafka partition + offset as unique identifier
            String messageId = event.getUserId() + "-updated-" + partition + "-" + offset;

            // Compute payload hash for conflict detection
            String payloadHash = idempotencyService.computeHash(eventJson);

            if (idempotencyService.isProcessed("community-service", messageId, payloadHash)) {
                log.debug("User {} already updated in cache (partition={}, offset={}), skipping",
                        event.getUserId(), partition, offset);
                return;
            }

            UUID userId = UUID.fromString(event.getUserId());
            authorCacheService.evict(userId);

            idempotencyService.markAsProcessed("community-service", messageId, payloadHash);
            log.info("Successfully invalidated author cache for user {} (partition={}, offset={})",
                    userId, partition, offset);

        } catch (IllegalStateException e) {
            // Payload conflict - poison pill
            log.error("Payload conflict detected for user.updated message | partition={} offset={} | {}",
                    partition, offset, e.getMessage());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Poison pill - skip message to unblock partition
            log.error("Malformed user.updated payload | partition={} offset={} error={}",
                    partition, offset, e.getMessage());
        } catch (Exception e) {
            // Re-throw for Kafka to retry
            log.error("Failed to process user.updated message | partition={} offset={} error={}",
                    partition, offset, e.getMessage(), e);
            throw new RuntimeException("Failed to process user.updated", e);
        }
    }
}
