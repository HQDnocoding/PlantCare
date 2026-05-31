package com.backend.auth.consumer;

import com.backend.auth.event.UserProfileResultEvent;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import com.backend.auth.repository.ProcessedMessageRepository;
import com.backend.auth.service.AuthService;
import com.backend.auth.entity.ProcessedMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserProfileResultConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;
    private final AuthService authService;

    @KafkaListener(topics = "user.profile.result", groupId = "auth-service")
    @Transactional
    public void consume(String message,
            @org.springframework.messaging.handler.annotation.Header("kafka_receivedPartitionId") int partition,
            @org.springframework.messaging.handler.annotation.Header("kafka_offset") long offset) {
        String messageKey = "user.profile.result:" + partition + ":" + offset;
        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.info("Duplicate profile result message skipped: {}", messageKey);
            return;
        }

        UserProfileResultEvent event;
        try {
            event = objectMapper.readValue(message, UserProfileResultEvent.class);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to parse user.profile.result payload");
        }

        if (event.getUserId() == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "userId is required in user.profile.result event");
        }

        if (!event.isSuccess()) {
            log.warn("Profile creation failed, rolling back auth user | userId={} reason={}", event.getUserId(),
                    event.getError());
            authService.softDeleteAuthUser(event.getUserId());
        } else {
            log.info("Profile creation succeeded | userId={}", event.getUserId());
            authService.activateAuthUser(event.getUserId());
        }

        processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());
    }
}
