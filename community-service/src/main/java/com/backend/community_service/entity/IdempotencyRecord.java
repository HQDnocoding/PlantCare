package com.backend.community_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique idempotency key: e.g., "userId:POST:/api/v1/posts:timestamp"
     * or from X-Idempotency-Key header
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * HTTP method: POST, PUT, DELETE, PATCH
     */
    @Column(name = "method", nullable = false, length = 20)
    private String method;

    /**
     * Request path: e.g., "/api/v1/posts"
     */
    @Column(name = "path", nullable = false, length = 500)
    private String path;

    /**
     * Response status code (e.g., 200, 201, 400, 500)
     */
    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    /**
     * Cached response body (JSON)
     */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /**
     * Created timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * TTL: when this record expires (24 hours default)
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Request hash for conflict detection
     */
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (expiresAt == null) {
            expiresAt = Instant.now().plusSeconds(86400); // 24 hours TTL
        }
    }
}
