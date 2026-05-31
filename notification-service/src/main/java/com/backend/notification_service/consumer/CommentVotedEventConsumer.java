package com.backend.notification_service.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.notification_service.entity.ProcessedMessage;
import com.backend.notification_service.event.CommentVotedEvent;
import com.backend.notification_service.repository.ProcessedMessageRepository;
import com.backend.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommentVotedEventConsumer {

    private final NotificationService notificationService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "comment.voted", groupId = "notification-service")
    @Transactional
    public void onCommentVoted(
            String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        String messageKey = "comment.voted:" + partition + ":" + offset;

        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate comment.voted skipped: {}", messageKey);
            return;
        }

        CommentVotedEvent event;
        try {
            event = objectMapper.readValue(payload, CommentVotedEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse comment.voted event", e);
            return;
        }

        try {
            log.info("[CommentVotedEventConsumer] Processing comment.voted | commentId={} | actorId={} | value={}",
                    event.getCommentId(), event.getActorId(), event.getValue());

            // Notify comment author about the vote
            UUID authorId = UUID.fromString(event.getCommentAuthorId());
            UUID actorId = UUID.fromString(event.getActorId());
            notificationService.notifyCommentAuthorOnVote(authorId, actorId, event);

            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());
            log.info("[CommentVotedEventConsumer] Successfully processed comment.voted | commentId={}",
                    event.getCommentId());

        } catch (Exception e) {
            log.error("[CommentVotedEventConsumer] Failed to process comment.voted | commentId={} | error={}",
                    event.getCommentId(), e.getMessage(), e);
        }
    }
}
