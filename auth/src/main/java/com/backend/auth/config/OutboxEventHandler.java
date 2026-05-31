package com.backend.auth.config;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.auth.common.OutboxStatus;
import com.backend.auth.entity.OutboxEvent;
import com.backend.auth.repository.OutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventHandler {

    private final OutboxRepository outboxRepo;

    @Transactional(transactionManager = "transactionManager")
    public void handleSendFailure(OutboxEvent event, Throwable ex) {
        log.warn("Outbox event {} send failed: {} | retryCount={}",
                event.getId(), ex != null ? ex.getMessage() : "Unknown error", event.getRetryCount());

        event.setRetryCount(event.getRetryCount() + 1);
        event.setStatus(OutboxStatus.FAILED);
        outboxRepo.save(event);
        log.error("Marked outbox event {} as FAILED", event.getId());
    }
}
