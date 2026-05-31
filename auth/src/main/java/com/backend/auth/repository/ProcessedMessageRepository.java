package com.backend.auth.repository;

import com.backend.auth.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {
    boolean existsByMessageKey(String messageKey);
}
