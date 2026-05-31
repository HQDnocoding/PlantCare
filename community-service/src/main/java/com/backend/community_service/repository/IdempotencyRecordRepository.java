package com.backend.community_service.repository;

import com.backend.community_service.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Find existing idempotency record by key
     */
    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find by idempotency key and user ID
     */
    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserId(String idempotencyKey, UUID userId);

    /**
     * Find by idempotency key, user ID, method and path (scoped idempotency)
     */
    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserIdAndMethodAndPath(String idempotencyKey, UUID userId,
            String method, String path);

    /**
     * Delete expired records (cleanup job)
     */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
    int deleteExpiredRecords(@Param("now") Instant now);

    /**
     * Count active records for user (monitoring)
     */
    @Query("SELECT COUNT(r) FROM IdempotencyRecord r " +
            "WHERE r.userId = :userId AND r.expiresAt > :now")
    long countActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
