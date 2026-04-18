package com.backend.auth.config;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.auth.entity.OutboxEvent;
import com.backend.auth.repository.OutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventHandler {

    private final OutboxRepository outboxRepo;
    private static final int MAX_RETRIES = 5;

    /**
     * Mark event as successfully processed.
     * Each event is committed independently (REQUIRES_NEW).
     */
    @Transactional(transactionManager = "transactionManager")
    public void markProcessed(UUID eventId) {
        outboxRepo.markProcessed(eventId);
        log.debug("Marked outbox event {} as processed", eventId);
    }

    /**
     * Handle send failure: increment retry count or move to DLQ.
     * Each failure is committed independently.
     */
    @Transactional(transactionManager = "transactionManager")
    public void handleSendFailure(OutboxEvent event, Throwable ex) {
        log.warn("Outbox event {} send failed: {} | retryCount={}",
                event.getId(), ex != null ? ex.getMessage() : "Unknown error", event.getRetryCount());

        if (event.getRetryCount() >= MAX_RETRIES) {
            outboxRepo.moveToDlq(event.getId());
            log.error("Moved outbox event {} to DLQ after {} retries",
                    event.getId(), MAX_RETRIES);
        } else {
            outboxRepo.incrementRetry(event.getId(), Instant.now());
            log.debug("Incremented retry count for outbox event {}", event.getId());
        }
    }
}
