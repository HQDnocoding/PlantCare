package com.backend.community_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.backend.community_service.entity.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Find events ready for retry with exponential backoff
     * Native query with FOR UPDATE for pessimistic locking
     */
    @Query("""
                SELECT e FROM OutboxEvent e
                WHERE e.processed = false
                  AND e.inDlq = false
                  AND (e.lastRetryAt IS NULL OR e.lastRetryAt < :retryBefore)
                ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> findRetryableEvents(
            @Param("retryBefore") Instant retryBefore,
            Pageable pageable);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.processed = true AND e.createdAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.inDlq = true AND e.createdAt < :cutoff")
    int deleteDlqBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.processed = true WHERE e.id = :id")
    void markProcessed(@Param("id") UUID id);

}
