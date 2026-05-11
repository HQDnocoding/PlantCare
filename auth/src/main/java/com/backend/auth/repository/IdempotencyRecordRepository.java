package com.backend.auth.repository;

import com.backend.auth.entity.IdempotencyRecord;
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

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserId(String idempotencyKey, UUID userId);

    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserIdAndMethodAndPath(String idempotencyKey, UUID userId,
            String method, String path);

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt < :now")
    int deleteExpiredRecords(@Param("now") Instant now);

    @Query("SELECT COUNT(r) FROM IdempotencyRecord r " +
            "WHERE r.userId = :userId AND r.expiresAt > :now")
    long countActiveByUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
