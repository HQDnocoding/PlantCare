package com.backend.notification_service.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.notification_service.entity.ProcessedMessage;
import com.backend.notification_service.event.PostVotedEvent;
import com.backend.notification_service.event.PostVotedNotifiedResultEvent;
import com.backend.notification_service.repository.ProcessedMessageRepository;
import com.backend.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostVotedEventConsumer {

    private final NotificationService notificationService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "post.voted", groupId = "notification-service")
    @Transactional
    public void onPostVoted(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        String messageKey = "post.voted:" + partition + ":" + offset;

        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate post.voted skipped: {}", messageKey);
            return;
        }

        PostVotedEvent event;
        try {
            event = objectMapper.readValue(payload, PostVotedEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse post.voted event", e);
            return;
        }

        try {
            log.info("[PostVotedEventConsumer] Processing post.voted | postId={} | actorId={} | value={}",
                    event.getPostId(), event.getActorId(), event.getValue());

            // Notify post author about the vote
            UUID authorId = UUID.fromString(event.getPostAuthorId());
            UUID actorId = UUID.fromString(event.getActorId());
            notificationService.notifyPostAuthorOnVote(authorId, actorId, event);

            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());
            log.info("[PostVotedEventConsumer] Successfully processed post.voted | postId={}", event.getPostId());

            // Publish success result
            publishResult(event.getPostId(), true, null);

        } catch (Exception e) {
            log.error("[PostVotedEventConsumer] Failed to process post.voted | postId={} | error={}",
                    event.getPostId(), e.getMessage(), e);

            // Publish failure result
            publishResult(event.getPostId(), false, e.getMessage());
        }
    }

    private void publishResult(String postId, boolean success, String error) {
        try {
            PostVotedNotifiedResultEvent result = PostVotedNotifiedResultEvent.builder()
                    .postId(postId)
                    .success(success)
                    .error(error)
                    .build();
            String payload = objectMapper.writeValueAsString(result);
            kafkaTemplate.send("post.voted.notified", postId, payload);
            log.info("Published post.voted.notified | postId={} | success={}", postId, success);
        } catch (Exception e) {
            log.error("Failed to publish post.voted.notified for postId={}", postId, e);
        }
    }
}
