package com.backend.scan_service.services;

import com.backend.scan_service.dto.SaveScanRequest;
import com.backend.scan_service.dto.ScanResponse;
import com.backend.scan_service.dto.UpdateConvRequest;
import com.backend.scan_service.entity.ScanHistory;
import com.backend.scan_service.exception.AppException;
import com.backend.scan_service.exception.ErrorCode;
import com.backend.scan_service.repository.ScanHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScanService {

    private final ScanHistoryRepository repo;
    private final FirebaseStorageService storageService;

    public ScanResponse save(UUID userId, SaveScanRequest req) {
        if (req.getImage() == null) {
            log.error("Image is null for userId={}", userId);
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        log.info("Uploading scan image for userId={}, filename={}, size={} bytes",
                userId, req.getImage().getOriginalFilename(), req.getImage().getSize());

        String imageUrl = storageService.uploadImage(req.getImage(), userId.toString());

        ScanHistory scan = ScanHistory.builder()
                .userId(userId)
                .imageUrl(imageUrl)
                .disease(req.getDisease())
                .confidence(BigDecimal.valueOf(req.getConfidence()))
                .confidentEnough(req.getConfidentEnough())
                .build();
        return toResponse(repo.save(scan));
    }

    public List<ScanResponse> getHistory(UUID userId) {
        return repo.findByUserIdOrderByScannedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public ScanResponse updateConv(UUID userId, UUID scanId, UpdateConvRequest req) {
        ScanHistory scan = repo.findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.SCAN_NOT_FOUND));
        scan.setConvId(req.getConvId());
        return toResponse(repo.save(scan));
    }

    public void delete(UUID userId, UUID scanId) {
        ScanHistory scan = repo.findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.SCAN_NOT_FOUND));
        repo.delete(scan);
    }

    private ScanResponse toResponse(ScanHistory s) {
        return ScanResponse.builder()
                .id(s.getId())
                .imageUrl(s.getImageUrl())
                .disease(s.getDisease())
                .confidence(s.getConfidence().doubleValue())
                .confidentEnough(s.isConfidentEnough())
                .convId(s.getConvId())
                .scannedAt(s.getScannedAt())
                .build();
    }
}