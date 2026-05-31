package com.backend.notification_service.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.notification_service.entity.ProcessedMessage;
import com.backend.notification_service.event.PostCreatedEvent;
import com.backend.notification_service.event.PostNotifiedResultEvent;
import com.backend.notification_service.repository.ProcessedMessageRepository;
import com.backend.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostCreatedEventConsumer {

    private final NotificationService notificationService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "post.created", groupId = "notification-service")
    @Transactional
    public void onPostCreated(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        String messageKey = "post.created:" + partition + ":" + offset;

        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate post.created skipped: {}", messageKey);
            return;
        }

        PostCreatedEvent event;
        try {
            event = objectMapper.readValue(payload, PostCreatedEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse post.created event", e);
            return;
        }

        try {
            log.info("[PostCreatedEventConsumer] Processing post.created | postId={} | authorId={}",
                    event.getPostId(), event.getAuthorId());

            // Notify followers of the author about the new post
            UUID authorId = UUID.fromString(event.getAuthorId());
            notificationService.notifyFollowersOnPostCreation(authorId, event);

            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());
            log.info("[PostCreatedEventConsumer] Successfully processed post.created | postId={}", event.getPostId());

            // Publish success result
            publishResult(event.getPostId(), true, null);

        } catch (Exception e) {
            log.error("[PostCreatedEventConsumer] Failed to process post.created | postId={} | error={}",
                    event.getPostId(), e.getMessage(), e);

            // Publish failure result
            publishResult(event.getPostId(), false, e.getMessage());
        }
    }

    private void publishResult(String postId, boolean success, String error) {
        try {
            PostNotifiedResultEvent result = PostNotifiedResultEvent.builder()
                    .postId(postId)
                    .success(success)
                    .error(error)
                    .build();
            String resultPayload = objectMapper.writeValueAsString(result);
            kafkaTemplate.send("post.notified.result", postId, resultPayload);
            log.info("Published post.notified.result | postId={} | success={}", postId, success);
        } catch (Exception e) {
            log.error("Failed to publish post.notified.result for postId={}", postId, e);
        }
    }
}
