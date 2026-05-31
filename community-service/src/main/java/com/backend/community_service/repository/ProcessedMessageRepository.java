package com.backend.community_service.repository;

import com.backend.community_service.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {
    boolean existsByMessageKey(String messageKey);
}
