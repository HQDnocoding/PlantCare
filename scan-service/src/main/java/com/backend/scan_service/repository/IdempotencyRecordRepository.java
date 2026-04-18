package com.backend.scan_service.repository;

import com.backend.scan_service.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for IdempotencyRecord persistence
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Find existing idempotency record by idempotency key and user ID
     */
    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserId(String idempotencyKey, UUID userId);

    /**
     * Find by idempotency key, user ID, method and path (scoped idempotency)
     */
    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserIdAndMethodAndPath(String idempotencyKey, UUID userId,
            String method, String path);

    /**
     * Delete expired records (for scheduled cleanup task)
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt <= :now")
    int deleteExpiredRecords(Instant now);
}
