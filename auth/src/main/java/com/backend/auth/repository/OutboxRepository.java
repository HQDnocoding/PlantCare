package com.backend.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.backend.auth.entity.OutboxEvent;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Find unprocessed or failed events that are ready for retry.
     * Excludes events still in DLQ.
     */
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.processed = false AND e.inDlq = false
            AND (e.createdAt <= :retryBefore OR e.lastRetryAt <= :retryBefore)
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> findRetryableEvents(@Param("retryBefore") Instant retryBefore, Pageable pageable);

    /**
     * Mark event as processed by setting processed=true and clearing retry info.
     */
    @Modifying
    @Query("""
            UPDATE OutboxEvent e SET e.processed = true, e.retryCount = 0, e.lastRetryAt = null
            WHERE e.id = :id
            """)
    void markProcessed(@Param("id") UUID id);

    /**
     * Increment retry count and update lastRetryAt timestamp.
     */
    @Modifying
    @Query("""
            UPDATE OutboxEvent e
            SET e.retryCount = e.retryCount + 1, e.lastRetryAt = :now
            WHERE e.id = :id
            """)
    void incrementRetry(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Move event to DLQ after max retries exceeded.
     */
    @Modifying
    @Query("""
            UPDATE OutboxEvent e SET e.inDlq = true
            WHERE e.id = :id
            """)
    void moveToDlq(@Param("id") UUID id);

    /**
     * Delete processed events older than cutoff.
     */
    @Modifying
    @Query("""
            DELETE FROM OutboxEvent e
            WHERE e.processed = true AND e.createdAt < :cutoff
            """)
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);

    /**
     * Delete DLQ events older than cutoff (for manual review).
     */
    @Modifying
    @Query("""
            DELETE FROM OutboxEvent e
            WHERE e.inDlq = true AND e.createdAt < :cutoff
            """)
    int deleteDlqBefore(@Param("cutoff") Instant cutoff);
}
