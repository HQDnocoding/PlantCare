package com.backend.user_service.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.user_service.common.OutboxStatus;
import com.backend.user_service.entity.OutboxEvent;
import com.backend.user_service.repository.OutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventHandler {

    private final OutboxRepository outboxRepo;

    @Transactional
    public void handleSendFailure(OutboxEvent event, Throwable ex) {
        event.setRetryCount(event.getRetryCount() + 1);
        event.setStatus(OutboxStatus.FAILED);
        outboxRepo.save(event);
        log.error("Marked outbox event {} as FAILED", event.getId());
    }
}
