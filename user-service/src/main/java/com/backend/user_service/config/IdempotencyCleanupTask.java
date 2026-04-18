package com.backend.user_service.config;

import com.backend.user_service.service.HttpIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled cleanup task for expired idempotency records.
 * Runs every 6 hours to remove records older than TTL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupTask {

    private final HttpIdempotencyService idempotencyService;

    @Scheduled(fixedDelayString = "${idempotency.cleanup-interval-ms:21600000}")
    public void cleanupExpiredRecords() {
        try {
            log.info("Starting idempotency record cleanup...");
            int deleted = idempotencyService.cleanupExpiredRecords();
            log.info("Cleanup completed: deleted {} expired records", deleted);
        } catch (Exception e) {
            log.error("Error during idempotency cleanup", e);
        }
    }
}
