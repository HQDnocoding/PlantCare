package com.backend.user_service.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.backend.user_service.common.OutboxStatus;
import com.backend.user_service.entity.OutboxEvent;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
        List<OutboxEvent> findByRetryableTrueAndStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                        Collection<OutboxStatus> statuses,
                        int retryCount,
                        Pageable pageable);
}