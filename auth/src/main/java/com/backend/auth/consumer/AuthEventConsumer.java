package com.backend.auth.consumer;

import com.backend.auth.event.UserDeletionEvent;
import com.backend.auth.event.UserDeletionResultEvent;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import com.backend.auth.repository.ProcessedMessageRepository;
import com.backend.auth.service.AuthService;
import com.backend.auth.service.OutboxService;
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
public class AuthEventConsumer {

    private final ObjectMapper objectMapper;
    private final ProcessedMessageRepository processedMessageRepository;
    private final AuthService authService;
    private final OutboxService outboxService;

    @KafkaListener(topics = "user.deleted", groupId = "auth-service")
    @Transactional
    public void consumeUserDeletedEvent(String message,
            @org.springframework.messaging.handler.annotation.Header("kafka_receivedPartitionId") int partition,
            @org.springframework.messaging.handler.annotation.Header("kafka_offset") long offset) {
        String messageKey = "user.deleted:" + partition + ":" + offset;
        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate user.deleted event skipped: {}", messageKey);
            return;
        }

        UserDeletionEvent event;
        try {
            event = objectMapper.readValue(message, UserDeletionEvent.class);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to parse user.deleted payload");
        }

        if (event.getUserId() == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "userId is required in user.deleted event");
        }

        try {
            authService.softDeleteAuthUser(event.getUserId());

            outboxService.save("user.deletion.result", event.getUserId(),
                    UserDeletionResultEvent.builder()
                            .userId(event.getUserId())
                            .success(true)
                            .error(null)
                            .build());

            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());

            log.info("Successfully deleted auth user {} (partition={}, offset={})",
                    event.getUserId(), partition, offset);

        } catch (Exception e) {
            publishDeletionFailureResult(event, e);
            log.error("Failed to delete auth user {}. Error: {}", event.getUserId(), e.getMessage(), e);
        }
    }

    private void publishDeletionFailureResult(UserDeletionEvent event, Exception exception) {
        try {
            outboxService.save("user.deletion.result", event.getUserId(),
                    UserDeletionResultEvent.builder()
                            .userId(event.getUserId())
                            .success(false)
                            .error(exception.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Cannot publish deletion failure result for user {}. Error: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
