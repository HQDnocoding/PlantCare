package com.backend.user_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backend.user_service.entity.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches unprocessed events eligible for retry.
     * - FOR UPDATE SKIP LOCKED: multiple instances skip rows locked by others,
     *   preventing duplicate processing in a horizontally-scaled environment.
     * - Ordered by retry_count ASC so newer failures are not starved.
     */
    @Query(value = "SELECT * FROM outbox_events "
            + "WHERE processed = false AND in_dlq = false "
            + "AND (last_retry_at IS NULL OR last_retry_at < :retryBefore) "
            + "ORDER BY retry_count ASC, created_at ASC "
            + "LIMIT :limit FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    List<OutboxEvent> findRetryableEvents(
            @Param("retryBefore") Instant retryBefore,
            @Param("limit") int limit);

    /** Marks a single event as processed using a direct UPDATE — avoids an extra SELECT. */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.processed = true WHERE e.id = :id")
    void markProcessed(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.processed = true AND e.createdAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.inDlq = true AND e.createdAt < :cutoff")
    int deleteDlqBefore(@Param("cutoff") Instant cutoff);
}