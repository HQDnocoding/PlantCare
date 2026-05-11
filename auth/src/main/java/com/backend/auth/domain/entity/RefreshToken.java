package com.backend.auth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing persistent Refresh Tokens for session management.
 * Stores a hashed version of the token for security and tracks metadata
 * such as device information and IP addresses to detect suspicious activity.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_token_hash", columnList = "token_hash"),
        @Index(name = "idx_user_id", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The owner of this refresh token.
     * Uses Lazy fetching to optimize performance.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Hashed version of the refresh token.
     * Never store raw tokens in the database to prevent theft via DB leaks.
     */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    /**
     * Metadata about the client's device (e.g., User-Agent).
     */
    @Column(name = "device_info")
    private String deviceInfo;

    /**
     * The IP address from which the token was issued.
     */
    @Column(name = "ip_address")
    private String ipAddress;

    /**
     * Expiration timestamp of the token.
     */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /**
     * Timestamp when the token was manually revoked (e.g., on logout).
     */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    /**
     * Automatic timestamp of when the record was created.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Checks if the token has passed its expiration date.
     * 
     * @return true if expired
     */
    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    /**
     * Checks if the token has been explicitly revoked.
     * 
     * @return true if revoked
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Checks if the token is still usable (neither expired nor revoked).
     * 
     * @return true if valid
     */
    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }

    /**
     * Sets the revocation timestamp to the current time.
     */
    public void revoke() {
        this.revokedAt = OffsetDateTime.now();
    }
}