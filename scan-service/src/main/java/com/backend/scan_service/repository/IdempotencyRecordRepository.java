package com.backend.scan_service.repository;

import com.backend.scan_service.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserId(String idempotencyKey, UUID userId);

    Optional<IdempotencyRecord> findByIdempotencyKeyAndUserIdAndMethodAndPath(String idempotencyKey, UUID userId,
            String method, String path);

    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.expiresAt <= :now")
    int deleteExpiredRecords(Instant now);
}
