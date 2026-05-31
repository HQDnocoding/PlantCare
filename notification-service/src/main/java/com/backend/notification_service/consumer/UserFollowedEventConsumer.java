package com.backend.notification_service.consumer;

import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.notification_service.entity.ProcessedMessage;
import com.backend.notification_service.event.UserFollowedEvent;
import com.backend.notification_service.repository.ProcessedMessageRepository;
import com.backend.notification_service.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.handler.annotation.Header;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserFollowedEventConsumer {

    private final NotificationService notificationService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.followed", groupId = "notification-service")
    @Transactional
    public void onCommentVoted(
            String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {

        String messageKey = "user.followed:" + partition + ":" + offset;

        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate user.followed skipped: {}", messageKey);
            return;
        }

        UserFollowedEvent event;
        try {
            event = objectMapper.readValue(payload, UserFollowedEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse user.followed event", e);
            return;
        }

        try {
            log.info("[UserFollowedEventConsumer] Processing user.followed | followingId={} | followerId={}",
                    event.getFollowingId(), event.getFollowerId());

            // Notify followed user about the new follower
            UUID followingId = UUID.fromString(event.getFollowingId());
            UUID followerId = UUID.fromString(event.getFollowerId());
            notificationService.notifyUserFollowed(followingId, followerId, event);

            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());
            log.info("[UserFollowedEventConsumer] Successfully processed user.followed | followingId={}",
                    event.getFollowingId());

        } catch (Exception e) {
            log.error("[UserFollowedEventConsumer] Failed to process user.followed | followingId={} | error={}",
                    event.getFollowingId(), e.getMessage(), e);
        }
    }
}
