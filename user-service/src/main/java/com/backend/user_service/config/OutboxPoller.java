package com.backend.user_service.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.backend.user_service.entity.OutboxEvent;
import com.backend.user_service.repository.OutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls unprocessed outbox events and forwards them to Kafka.
 * Each event's outcome (success / failure) is committed in an isolated
 * transaction via OutboxEventHandler — a separate Spring bean so AOP works.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    /** Injected as a Spring proxy so @Transactional on its methods is active. */
    private final OutboxEventHandler eventHandler;

    @Value("${outbox.max-retries:5}")
    private int maxRetries;

    @Value("${outbox.initial-wait-seconds:60}")
    private long initialWaitSeconds;

    @Value("${outbox.batch-size:50}")
    private int batchSize;

    /**
     * Fetches retryable events and sends them to Kafka.
     * Only events whose lastRetryAt is older than initialWaitSeconds are selected,
     * giving the minimum guaranteed wait between successive attempts.
     */
    @Transactional
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    public void poll() {
        // Events with lastRetryAt older than this threshold are eligible for retry
        Instant retryBefore = Instant.now().minusSeconds(initialWaitSeconds);

        List<OutboxEvent> events = outboxRepo.findRetryableEvents(retryBefore, batchSize);
        if (events.isEmpty()) return;

        log.debug("Outbox poller fetched {} event(s)", events.size());
        events.forEach(this::sendToKafka);
    }

    private void sendToKafka(OutboxEvent event) {
        kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // eventHandler is a Spring proxy — REQUIRES_NEW transaction works
                        eventHandler.handleSendFailure(event, ex);
                    } else {
                        eventHandler.markProcessed(event.getId());
                        log.debug("Sent outbox event {} to topic {}", event.getId(), event.getTopic());
                    }
                });
    }

    /** Deletes successfully processed events older than 7 days. */
    @Transactional
    @Scheduled(cron = "${outbox.cleanup.processed-cron:0 0 3 * * *}")
    public void cleanupProcessed() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = outboxRepo.deleteProcessedBefore(cutoff);
        log.info("Cleanup processed outbox events | deleted={} cutoff={}", deleted, cutoff);
    }

    /** Deletes DLQ events older than 30 days. */
    @Transactional
    @Scheduled(cron = "${outbox.cleanup.dlq-cron:0 0 4 * * *}")
    public void cleanupDlq() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = outboxRepo.deleteDlqBefore(cutoff);
        log.info("Cleanup DLQ outbox events | deleted={} cutoff={}", deleted, cutoff);
    }
}