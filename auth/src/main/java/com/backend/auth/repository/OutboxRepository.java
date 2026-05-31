package com.backend.auth.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.backend.auth.common.OutboxStatus;
import com.backend.auth.entity.OutboxEvent;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
        List<OutboxEvent> findByRetryableTrueAndStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                        Collection<OutboxStatus> statuses,
                        int retryCount,
                        Pageable pageable);
}
