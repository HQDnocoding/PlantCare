package com.backend.user_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.user_service.dto.CreateProfileRequest;
import com.backend.user_service.entity.ProcessedMessage;
import com.backend.user_service.event.UserDeletionEvent;
import com.backend.user_service.event.UserDeletionResultEvent;
import com.backend.user_service.event.UserProfileResultEvent;
import com.backend.user_service.repository.ProcessedMessageRepository;
import com.backend.user_service.service.OutboxService;
import com.backend.user_service.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final UserService userService;
    private final OutboxService outboxService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.created", groupId = "user-service")
    @Transactional
    public void consumeUserCreatedEvent(
            @Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        String messageKey = "user.created:" + partition + ":" + offset;
        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate message skipped: {}", messageKey);
            return;
        }

        try {
            log.debug("Received user.created event: {}", payload);

            CreateProfileRequest request = objectMapper.readValue(payload, CreateProfileRequest.class);

            userService.createProfile(request.getUserId(), request.getDisplayName(), request.getAvatarUrl());

            outboxService.save("user.profile.result", request.getUserId(), UserProfileResultEvent.builder()
                    .userId(request.getUserId())
                    .success(true)
                    .error(null)
                    .build());

            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());

            log.info("Successfully processed user.created event for user {} (partition={}, offset={})",
                    request.getUserId(), partition, offset);

        } catch (Exception e) {
            publishFailureResult(payload, e);
            log.error("Failed to process user.created event. Payload: {} | Error: {}",
                    payload, e.getMessage(), e);

        }
    }

    private void publishFailureResult(String payload, Exception exception) {
        try {
            CreateProfileRequest request = objectMapper.readValue(payload, CreateProfileRequest.class);
            outboxService.save("user.profile.result", request.getUserId(), UserProfileResultEvent.builder()
                    .userId(request.getUserId())
                    .success(false)
                    .error(exception.getMessage())
                    .build());
        } catch (Exception parsingException) {
            log.error("Cannot publish failure result for user.created due to invalid payload: {}", payload);
        }
    }
}
