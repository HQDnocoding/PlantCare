package com.backend.scan_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency records for scan-service requests.
 * Stores responses for idempotent endpoints to prevent duplicates.
 *
 * Key: SHA-256 hash of request + idempotency header
 * Used to detect retries and return cached response without re-processing
 */
@Entity
@Table(name = "idempotency_records", indexes = {
        @Index(name = "idx_user_method_path", columnList = "user_id, method, path"),
        @Index(name = "idx_request_hash", columnList = "request_hash"),
        @Index(name = "idx_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Idempotency key from X-Idempotency-Key header
     * or generated from API Gateway deterministic hash
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /**
     * User ID making the request (from X-User-Id header)
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * HTTP method (POST, PUT, DELETE, PATCH)
     */
    @Column(name = "method", nullable = false, length = 10)
    private String method;

    /**
     * API endpoint path
     */
    @Column(name = "path", nullable = false)
    private String path;

    /**
     * SHA-256 hash of request body
     * Used for conflict detection - if same key but different body, reject
     */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    /**
     * Cached HTTP response body (JSON)
     */
    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    /**
     * HTTP response status code
     */
    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    /**
     * When record was created
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * When record expires (for TTL cleanup)
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
