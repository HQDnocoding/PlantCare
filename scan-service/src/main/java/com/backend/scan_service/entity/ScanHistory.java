package com.backend.scan_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "scan_history")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ScanHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "disease", nullable = false)
    private String disease;

    @Column(name = "confidence", nullable = false)
    private BigDecimal confidence;

    @Column(name = "confident_enough", nullable = false)
    private boolean confidentEnough;

    @Column(name = "conv_id")
    private String convId;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    @PrePersist
    public void prePersist() {
        scannedAt = LocalDateTime.now();
    }
}
