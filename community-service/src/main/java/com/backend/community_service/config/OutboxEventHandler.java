package com.backend.community_service.config;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backend.community_service.entity.OutboxEvent;
import com.backend.community_service.repository.OutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventHandler {

    private final OutboxRepository outboxRepo;

    @Value("${outbox.max-retries:5}")
    private int maxRetries;

    @Value("${outbox.initial-wait-seconds:60}")
    private long initialWaitSeconds;

    /**
     * Marks event as successfully processed.
     * REQUIRES_NEW — runs in its own transaction independent of caller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID eventId) {
        outboxRepo.markProcessed(eventId); // @Modifying query, no findById
        log.debug("Outbox event {} marked as processed", eventId);
    }

    /**
     * Handles send failure — increments retry count or moves to DLQ.
     * REQUIRES_NEW — must commit even if outer transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSendFailure(OutboxEvent event, Throwable ex) {
        int nextRetry = event.getRetryCount() + 1;
        event.setRetryCount(nextRetry);
        event.setLastRetryAt(Instant.now());

        if (nextRetry <= maxRetries) {
            outboxRepo.save(event);
            log.warn("Outbox event {} failed (retry #{}) | error={}",
                    event.getId(), nextRetry, ex.getMessage());
        } else {
            event.setInDlq(true);
            outboxRepo.save(event);
            log.error("Outbox event {} moved to DLQ after {} retries | topic={} | error={}",
                    event.getId(), maxRetries, event.getTopic(), ex.getMessage());
        }
    }

    /**
     * Calculates exponential backoff delay in seconds.
     */
    public long calculateBackoff(int retryCount) {
        return initialWaitSeconds * (long) Math.pow(2, retryCount);
    }
}
