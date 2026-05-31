package com.backend.notification_service.repository;

import com.backend.notification_service.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {
    boolean existsByMessageKey(String messageKey);
}
