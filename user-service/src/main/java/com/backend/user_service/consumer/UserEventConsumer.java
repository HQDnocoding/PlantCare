package com.backend.user_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.backend.user_service.dto.CreateProfileRequest;
import com.backend.user_service.service.IdempotencyService;
import com.backend.user_service.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final UserService userService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.created", groupId = "user-service")
    public void consumeUserCreatedEvent(
            @Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        try {
            log.debug("Received user.created event: {}", payload);

            CreateProfileRequest request = objectMapper.readValue(payload, CreateProfileRequest.class);

            String messageId = request.getUserId() + "-created-" + partition + "-" + offset;

            String payloadHash = idempotencyService.computeHash(payload);

            if (idempotencyService.isProcessed("user-service", messageId, payloadHash)) {
                log.debug("User {} already created (partition={}, offset={}), skipping",
                        request.getUserId(), partition, offset);
                return;
            }

            userService.createProfile(request.getUserId(), request.getDisplayName(), request.getAvatarUrl());

            idempotencyService.markAsProcessed("user-service", messageId, payloadHash);
            log.info("Successfully processed user.created event for user {} (partition={}, offset={})",
                    request.getUserId(), partition, offset);

        } catch (IllegalStateException e) {
            // Payload conflict - poison pill
            log.error("Payload conflict detected for user.created message | partition={} offset={} | {}",
                    partition, offset, e.getMessage());

        } catch (Exception e) {
            log.error("Failed to process user.created event. Payload: {} | Error: {}",
                    payload, e.getMessage(), e);

        }
    }
}
