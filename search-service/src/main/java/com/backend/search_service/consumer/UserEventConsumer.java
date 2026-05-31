package com.backend.search_service.consumer;

import com.backend.search_service.event.UserDeletedEvent;
import com.backend.search_service.event.UserUpdatedEvent;
import com.backend.search_service.service.IdempotencyService;
import com.backend.search_service.service.IndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final IndexService indexService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.updated", groupId = "search-service")
    public void onUserUpdated(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        try {
            UserUpdatedEvent event = objectMapper.readValue(payload, UserUpdatedEvent.class);
            // Use Kafka partition + offset as unique identifier
            String messageId = event.getUserId() + "-updated-" + partition + "-" + offset;

            if (idempotencyService.isProcessed("search-service", messageId)) {
                log.debug("User {} already updated in index (partition={}, offset={}), skipping",
                        event.getUserId(), partition, offset);
                return;
            }

            indexService.indexOrUpdateUser(event);
            idempotencyService.markAsProcessed("search-service", messageId);
            log.info("Successfully updated user {} in index (partition={}, offset={})",
                    event.getUserId(), partition, offset);

        } catch (Exception e) {
            log.error("Failed to process UserUpdatedEvent: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "user.deleted", groupId = "search-service")
    public void onUserDeleted(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        try {
            UserDeletedEvent event = objectMapper.readValue(payload, UserDeletedEvent.class);
            // Use Kafka partition + offset as unique identifier
            String messageId = event.getUserId() + "-deleted-" + partition + "-" + offset;

            if (idempotencyService.isProcessed("search-service", messageId)) {
                log.debug("User {} already deleted from index (partition={}, offset={}), skipping",
                        event.getUserId(), partition, offset);
                return;
            }

            indexService.deleteUser(event.getUserId());
            idempotencyService.markAsProcessed("search-service", messageId);
            log.info("Successfully deleted user {} from index (partition={}, offset={})",
                    event.getUserId(), partition, offset);

        } catch (Exception e) {
            log.error("Failed to process UserDeletedEvent: {}", e.getMessage(), e);
        }
    }
}