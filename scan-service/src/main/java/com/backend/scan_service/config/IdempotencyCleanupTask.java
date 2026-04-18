package com.backend.scan_service.config;

import com.backend.scan_service.service.HttpIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to cleanup expired idempotency records.
 *
 * Runs every 6 hours to delete records that have exceeded their TTL.
 * TTL is typically 24 hours, so one cleanup runs before any record expires.
 *
 * This keeps the idempotency_records table bounded in size
 * and ensures records expire predictably.
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupTask {

    private final HttpIdempotencyService idempotencyService;

    /**
     * Run cleanup every 6 hours (21600000 milliseconds)
     * Deletes all expired idempotency records from the database
     */
    @Scheduled(fixedRate = 21600000) // 6 hours
    public void cleanupExpiredRecords() {
        try {
            log.info("Starting idempotency record cleanup...");
            int deleted = idempotencyService.cleanupExpiredRecords();
            log.info(" Cleaned up {} expired idempotency records", deleted);
        } catch (Exception e) {
            log.error("❌ Idempotency cleanup task failed", e);
            // Don't throw - let the scheduler retry next cycle
        }
    }
}
