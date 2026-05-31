package com.backend.user_service.config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.backend.user_service.common.OutboxStatus;
import com.backend.user_service.entity.OutboxEvent;
import com.backend.user_service.repository.OutboxRepository;
import com.backend.user_service.service.OutboxEventCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.max-retries:8}")
    private int maxRetries;

    @Value("${outbox.initial-wait-seconds:30}")
    private long initialWaitSeconds;

    @Value("${outbox.retry-batch-size:100}")
    private int retryBatchSize;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutboxEventCreated(OutboxEventCreatedEvent eventCreated) {
        UUID outboxEventId = eventCreated.outboxEventId();
        outboxRepo.findById(outboxEventId)
                .filter(event -> event.getStatus() == OutboxStatus.PENDING)
                .ifPresent(this::publish);
    }

    @Scheduled(fixedDelayString = "${outbox.retry-interval-ms:60000}")
    @Transactional
    public void retryFailedEvents() {
        List<OutboxEvent> events = outboxRepo.findByRetryableTrueAndStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                maxRetries,
                PageRequest.of(0, retryBatchSize));

        for (OutboxEvent event : events) {
            if (shouldRetryNow(event)) {
                publish(event);
            }
        }
    }

    @Transactional
    public void publish(OutboxEvent outboxEvent) {
        try {
            kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getAggregateId(), outboxEvent.getPayload())
                    .get();
            outboxEvent.markPublished();
        } catch (Exception e) {
            outboxEvent.setRetryCount(outboxEvent.getRetryCount() + 1);
            outboxEvent.markFailed();
            log.error("Failed to publish outbox event {} | topic={} | reason={}",
                    outboxEvent.getId(), outboxEvent.getTopic(), e.getMessage(), e);
        }
        outboxRepo.save(outboxEvent);
    }

    private boolean shouldRetryNow(OutboxEvent event) {
        if (event.getStatus() == OutboxStatus.PENDING) {
            return true;
        }
        long waitSeconds = calculateBackoffSeconds(event.getRetryCount());
        long ageSeconds = Duration.between(event.getCreatedAt(), Instant.now()).getSeconds();
        return ageSeconds >= waitSeconds;
    }

    private long calculateBackoffSeconds(int retryCount) {
        int exponent = Math.max(0, Math.min(retryCount - 1, 8));
        long wait = initialWaitSeconds * (1L << exponent);
        return Math.min(wait, 900L);
    }
}