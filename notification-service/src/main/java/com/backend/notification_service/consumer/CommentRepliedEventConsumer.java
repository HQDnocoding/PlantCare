package com.backend.notification_service.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.notification_service.entity.ProcessedMessage;
import com.backend.notification_service.event.CommentRepliedEvent;
import com.backend.notification_service.repository.ProcessedMessageRepository;
import com.backend.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.handler.annotation.Header;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommentRepliedEventConsumer {

    private final NotificationService notificationService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "comment.replied", groupId = "notification-service")
    @Transactional
    public void onCommentReplied(String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        String messageKey = "comment.replied:" + partition + ":" + offset;

        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate comment.replied skipped: {}", messageKey);
            return;
        }

        CommentRepliedEvent event;
        try {
            event = objectMapper.readValue(payload, CommentRepliedEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse comment.replied event", e);
            return;
        }

        try {
            log.info("[CommentRepliedEventConsumer] Processing comment.replied | commentId={} | actorId={}",
                    event.getCommentId(), event.getActorId());

            // Notify parent comment author about the reply
            UUID parentAuthorId = UUID.fromString(event.getParentCommentAuthorId());
            UUID actorId = UUID.fromString(event.getActorId());
            notificationService.notifyCommentReplied(parentAuthorId, actorId, event);

            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());
            log.info("[CommentRepliedEventConsumer] Successfully processed comment.replied | commentId={}",
                    event.getCommentId());

        } catch (Exception e) {
            log.error("[CommentRepliedEventConsumer] Failed to process comment.replied | commentId={} | error={}",
                    event.getCommentId(), e.getMessage(), e);
        }
    }
}
