package com.backend.scan_service.repository;

import com.backend.scan_service.entity.ScanHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ScanHistoryRepository extends JpaRepository<ScanHistory, UUID> {
    List<ScanHistory> findByUserIdOrderByScannedAtDesc(UUID userId);
    Optional<ScanHistory> findByIdAndUserId(UUID id, UUID userId);
}