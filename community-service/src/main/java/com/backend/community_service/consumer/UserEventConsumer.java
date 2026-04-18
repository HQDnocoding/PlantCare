package com.backend.community_service.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.backend.community_service.event.UserDeletedEvent;
import com.backend.community_service.service.IdempotencyService;
import com.backend.community_service.service.PostService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka consumer for user deletion events
 * Prevents duplicate deletion processing using idempotency with Kafka offset
 * tracking
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final PostService postService;
    private final IdempotencyService idempotencyService;

    /**
     * Listen for user.deleted events
     * Deletes all posts belonging to the deleted user
     * Uses Kafka partition+offset for truly unique message identification
     */
    @KafkaListener(topics = "user.deleted", groupId = "community-service")
    public void onUserDeleted(@Payload UserDeletedEvent event,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        // Use Kafka partition + offset as unique identifier (guaranteed unique per
        // partition)
        String messageId = event.getUserId() + "-deleted-" + partition + "-" + offset;

        if (idempotencyService.isProcessed("community-service", messageId)) {
            log.debug("User deletion for {} already processed (partition={}, offset={}), skipping",
                    event.getUserId(), partition, offset);
            return;
        }

        try {
            log.info("Processing user.deleted for userId={}", event.getUserId());
            UUID userId = UUID.fromString(event.getUserId());

            // Delete all posts by this user
            postService.deleteAllPostsByUser(userId);
            log.info("Deleted all posts for user {} (partition={}, offset={})", userId, partition, offset);

            // Mark as successfully processed
            idempotencyService.markAsProcessed("community-service", messageId);
            log.info("Successfully processed user deletion message {}", messageId);

        } catch (Exception e) {
            log.error("Error processing user deletion message {}: {}", messageId, e.getMessage(), e);
            // Don't mark as processed → will retry on next consumer startup
        }
    }
}