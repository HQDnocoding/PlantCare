package com.backend.auth.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.backend.auth.entity.OutboxEvent;
import com.backend.auth.repository.OutboxRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventHandler eventHandler;

    @Value("${outbox.poll-interval-ms:5000}")
    private long pollIntervalMs;

    @Value("${outbox.initial-wait-seconds:60}")
    private long initialWaitSeconds;

    @Value("${outbox.batch-size:100}")
    private int batchSize;

    /**
     * Polls unprocessed outbox events and sends them to Kafka.
     * Runs every 5 seconds by default.
     */
    @Transactional
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    public void poll() {
        Instant retryBefore = Instant.now().minusSeconds(initialWaitSeconds);

        List<OutboxEvent> events = outboxRepo.findRetryableEvents(
                retryBefore,
                PageRequest.of(0, batchSize));

        if (events.isEmpty())
            return;

        log.debug("Outbox poller fetched {} events for auth service", events.size());
        events.forEach(this::sendToKafka);
    }

    private void sendToKafka(OutboxEvent event) {
        kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        eventHandler.handleSendFailure(event, ex);
                        return;
                    }
                    eventHandler.markProcessed(event.getId());
                });
    }

    /**
     * Cleanup processed events older than 7 days (cron: 3 AM daily).
     */
    @Transactional
    @Scheduled(cron = "${outbox.cleanup.processed-cron:0 0 3 * * *}")
    public void cleanupProcessed() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        int deleted = outboxRepo.deleteProcessedBefore(cutoff);
        log.info("Cleanup processed outbox events | deleted={} cutoff={}", deleted, cutoff);
    }

    /**
     * Cleanup DLQ events older than 30 days (cron: 4 AM daily).
     */
    @Transactional
    @Scheduled(cron = "${outbox.cleanup.dlq-cron:0 0 4 * * *}")
    public void cleanupDlq() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = outboxRepo.deleteDlqBefore(cutoff);
        log.info("Cleanup DLQ outbox events | deleted={} cutoff={}", deleted, cutoff);
    }
}
