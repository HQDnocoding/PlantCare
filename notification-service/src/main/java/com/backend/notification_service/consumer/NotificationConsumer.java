package com.backend.notification_service.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.backend.notification_service.event.CommentEvent;
import com.backend.notification_service.event.FollowEvent;
import com.backend.notification_service.event.ReplyEvent;
import com.backend.notification_service.event.VoteEvent;
import com.backend.notification_service.service.IdempotencyService;
import com.backend.notification_service.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    private static final String SERVICE_ID = "notification-consumer";

    @KafkaListener(topics = "user.followed", groupId = "notification-service")
    public void onFollow(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        String messageId = "follow-" + partition + "-" + offset;

        if (idempotencyService.isProcessed(SERVICE_ID, messageId)) {
            log.debug("Duplicate user.followed skipped | partition={} offset={}", partition, offset);
            return;
        }

        try {
            FollowEvent e = objectMapper.readValue(payload, FollowEvent.class);

            notificationService.createAndSend(
                    UUID.fromString(e.getFollowingId()),
                    "FOLLOW",
                    "Người theo dõi mới",
                    e.getFollowerName() + " đã theo dõi bạn",
                    UUID.fromString(e.getFollowerId()),
                    e.getFollowerName(),
                    UUID.fromString(e.getFollowerId()),
                    "USER");

            idempotencyService.markAsProcessed(SERVICE_ID, messageId);
            log.info("user.followed processed | partition={} offset={}", partition, offset);

        } catch (JsonProcessingException ex) {
            // Poison pill — skip to unblock partition
            log.error("Malformed user.followed payload | partition={} offset={} error={}",
                    partition, offset, ex.getMessage());
        } catch (Exception ex) {
            // Re-throw to prevent offset commit — Kafka will retry
            throw new RuntimeException("Failed to process user.followed", ex);
        }
    }

    @KafkaListener(topics = "post.commented", groupId = "notification-service")
    public void onComment(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        String messageId = "comment-" + partition + "-" + offset;

        if (idempotencyService.isProcessed(SERVICE_ID, messageId)) {
            log.debug("Duplicate post.commented skipped | partition={} offset={}", partition, offset);
            return;
        }

        try {
            CommentEvent e = objectMapper.readValue(payload, CommentEvent.class);

            notificationService.createAndSend(
                    UUID.fromString(e.getPostAuthorId()),
                    "COMMENT",
                    "Bình luận mới",
                    e.getActorName() + " đã bình luận bài viết của bạn",
                    UUID.fromString(e.getActorId()),
                    e.getActorName(),
                    UUID.fromString(e.getPostId()),
                    "POST");

            idempotencyService.markAsProcessed(SERVICE_ID, messageId);
            log.info("post.commented processed | partition={} offset={}", partition, offset);

        } catch (JsonProcessingException ex) {
            log.error("Malformed post.commented payload | partition={} offset={} error={}",
                    partition, offset, ex.getMessage());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to process post.commented", ex);
        }
    }

    @KafkaListener(topics = "post.voted", groupId = "notification-service")
    public void onVote(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        String messageId = "vote-" + partition + "-" + offset;

        if (idempotencyService.isProcessed(SERVICE_ID, messageId)) {
            log.debug("Duplicate post.voted skipped | partition={} offset={}", partition, offset);
            return;
        }

        try {
            VoteEvent e = objectMapper.readValue(payload, VoteEvent.class);

            // Only notify on upvote — downvotes are intentionally ignored
            if (e.getValue() != 1)
                return;

            notificationService.createAndSend(
                    UUID.fromString(e.getPostAuthorId()),
                    "VOTE",
                    "Upvote mới",
                    e.getActorName() + " đã upvote bài viết của bạn",
                    UUID.fromString(e.getActorId()),
                    e.getActorName(),
                    UUID.fromString(e.getPostId()),
                    "POST");

            idempotencyService.markAsProcessed(SERVICE_ID, messageId);
            log.info("post.voted processed | partition={} offset={}", partition, offset);

        } catch (JsonProcessingException ex) {
            log.error("Malformed post.voted payload | partition={} offset={} error={}",
                    partition, offset, ex.getMessage());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to process post.voted", ex);
        }
    }

    @KafkaListener(topics = "comment.replied", groupId = "notification-service")
    public void onReply(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        String messageId = "reply-" + partition + "-" + offset;

        if (idempotencyService.isProcessed(SERVICE_ID, messageId)) {
            log.debug("Duplicate comment.replied skipped | partition={} offset={}", partition, offset);
            return;
        }

        try {
            ReplyEvent e = objectMapper.readValue(payload, ReplyEvent.class);

            notificationService.createAndSend(
                    UUID.fromString(e.getParentCommentAuthorId()),
                    "REPLY",
                    "Có người trả lời bình luận",
                    e.getActorName() + " đã trả lời bình luận của bạn",
                    UUID.fromString(e.getActorId()),
                    e.getActorName(),
                    UUID.fromString(e.getCommentId()),
                    "COMMENT");

            idempotencyService.markAsProcessed(SERVICE_ID, messageId);
            log.info("comment.replied processed | partition={} offset={}", partition, offset);

        } catch (JsonProcessingException ex) {
            log.error("Malformed comment.replied payload | partition={} offset={} error={}",
                    partition, offset, ex.getMessage());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to process comment.replied", ex);
        }
    }
}