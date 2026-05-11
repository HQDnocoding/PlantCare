package com.backend.user_service.config;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backend.user_service.entity.OutboxEvent;
import com.backend.user_service.repository.OutboxRepository;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(UUID eventId) {
        outboxRepo.markProcessed(eventId);
        log.debug("Outbox event {} marked as processed", eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSendFailure(OutboxEvent event, Throwable ex) {
        int nextRetry = event.getRetryCount() + 1;
        event.setRetryCount(nextRetry);
        event.setLastRetryAt(Instant.now());

        if (nextRetry <= maxRetries) {
            outboxRepo.save(event);
            long nextWaitSeconds = initialWaitSeconds * (long) Math.pow(2, nextRetry);
            log.warn("Outbox event {} failed (retry #{}) — next retry in ~{}s | error={}",
                    event.getId(), nextRetry, nextWaitSeconds, ex.getMessage());
        } else {
            // Max retries exceeded — move to Dead Letter Queue
            event.setInDlq(true);
            outboxRepo.save(event);
            log.error("Outbox event {} moved to DLQ after {} retries | topic={} | error={}",
                    event.getId(), maxRetries, event.getTopic(), ex.getMessage());
        }
    }

    /** Returns exponential backoff delay in seconds for a given retry attempt. */
    public long calculateBackoff(int retryCount) {
        return initialWaitSeconds * (long) Math.pow(2, retryCount);
    }
}
