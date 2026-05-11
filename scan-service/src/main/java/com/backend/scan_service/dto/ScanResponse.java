package com.backend.scan_service.dto;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class ScanResponse {
    private UUID id;
    private String imageUrl;
    private String disease;
    private Double confidence;
    private Boolean confidentEnough;
    private String convId;
    private LocalDateTime scannedAt;
}