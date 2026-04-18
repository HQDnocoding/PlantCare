package com.backend.search_service.consumer;

import com.backend.search_service.event.PostCreatedEvent;
import com.backend.search_service.event.PostDeletedEvent;
import com.backend.search_service.event.PostUpdatedEvent;
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
public class PostEventConsumer {

    private final IndexService indexService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "post.created", groupId = "search-service")
    public void onCreated(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        try {
            log.info("[PostEventConsumer] Received post.created | partition={} | offset={}", partition, offset);
            log.debug("[PostEventConsumer] Event payload: {}", payload);

            PostCreatedEvent event = objectMapper.readValue(payload, PostCreatedEvent.class);
            log.info("[PostEventConsumer] Parsed event | postId={} | content_length={} | tags={}",
                    event.getPostId(),
                    event.getContent() != null ? event.getContent().length() : 0,
                    event.getTags());

            // Use Kafka partition + offset as truly unique identifier
            String messageId = event.getPostId() + "-" + partition + "-" + offset;

            if (idempotencyService.isProcessed("search-service", messageId)) {
                log.debug("[PostEventConsumer] Post {} already indexed (partition={}, offset={}), skipping",
                        event.getPostId(), partition, offset);
                return;
            }

            indexService.indexPost(event);
            idempotencyService.markAsProcessed("search-service", messageId);
            log.info("[PostEventConsumer] Successfully indexed post {} (partition={}, offset={})",
                    event.getPostId(), partition, offset);

        } catch (Exception e) {
            log.error("[PostEventConsumer] Failed to process PostCreatedEvent | partition={} | offset={} | error={}",
                    partition, offset, e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "post.updated", groupId = "search-service")
    public void onUpdated(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        try {
            log.info("[PostEventConsumer] Received post.updated | partition={} | offset={}", partition, offset);

            PostUpdatedEvent event = objectMapper.readValue(payload, PostUpdatedEvent.class);
            log.info("[PostEventConsumer] Parsed update event | postId={}", event.getPostId());

            String messageId = event.getPostId() + "-updated-" + partition + "-" + offset;

            if (idempotencyService.isProcessed("search-service", messageId)) {
                log.debug("[PostEventConsumer] Post {} already updated in index (partition={}, offset={}), skipping",
                        event.getPostId(), partition, offset);
                return;
            }

            indexService.updatePost(event);
            idempotencyService.markAsProcessed("search-service", messageId);
            log.info("[PostEventConsumer] Successfully updated post {} in index (partition={}, offset={})",
                    event.getPostId(), partition, offset);

        } catch (Exception e) {
            log.error("[PostEventConsumer] Failed to process PostUpdatedEvent | partition={} | offset={} | error={}",
                    partition, offset, e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "post.deleted", groupId = "search-service")
    public void onDeleted(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        try {
            log.info("[PostEventConsumer] Received post.deleted | partition={} | offset={}", partition, offset);

            PostDeletedEvent event = objectMapper.readValue(payload, PostDeletedEvent.class);
            log.info("[PostEventConsumer] Parsed delete event | postId={}", event.getPostId());

            String messageId = event.getPostId() + "-deleted-" + partition + "-" + offset;

            if (idempotencyService.isProcessed("search-service", messageId)) {
                log.debug("[PostEventConsumer] Post {} already deleted from index (partition={}, offset={}), skipping",
                        event.getPostId(), partition, offset);
                return;
            }

            indexService.deletePost(event.getPostId());
            idempotencyService.markAsProcessed("search-service", messageId);
            log.info("[PostEventConsumer] Successfully deleted post {} from index (partition={}, offset={})",
                    event.getPostId(), partition, offset);

        } catch (Exception e) {
            log.error("[PostEventConsumer] Failed to process PostDeletedEvent | partition={} | offset={} | error={}",
                    partition, offset, e.getMessage(), e);
        }
    }
}