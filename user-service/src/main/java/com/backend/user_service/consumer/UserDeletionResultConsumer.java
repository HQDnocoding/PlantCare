package com.backend.user_service.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.user_service.entity.ProcessedMessage;
import com.backend.user_service.event.UserDeletionResultEvent;
import com.backend.user_service.exception.AppException;
import com.backend.user_service.exception.ErrorCode;
import com.backend.user_service.repository.ProcessedMessageRepository;
import com.backend.user_service.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDeletionResultConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;
    private final UserService userService;

    @KafkaListener(topics = "user.deletion.result", groupId = "user-service")
    @Transactional
    public void consume(String message,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        String messageKey = "user.deletion.result:" + partition + ":" + offset;
        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate deletion result message skipped: {}", messageKey);
            return;
        }

        UserDeletionResultEvent event;
        try {
            event = objectMapper.readValue(message, UserDeletionResultEvent.class);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to parse user.deletion.result payload");
        }

        if (event.getUserId() == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "userId is required in user.deletion.result event");
        }

        if (!event.isSuccess()) {
            log.warn("Auth user deletion failed, rolling back user profile | userId={} reason={}",
                    event.getUserId(), event.getError());
            userService.restoreProfile(event.getUserId());
        } else {
            log.info("User deletion completed successfully | userId={}", event.getUserId());
        }

        processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());
    }
}
