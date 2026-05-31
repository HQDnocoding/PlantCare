package com.backend.scan_service.config;

import com.backend.scan_service.service.HttpIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCleanupTask {

    private final HttpIdempotencyService idempotencyService;

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
