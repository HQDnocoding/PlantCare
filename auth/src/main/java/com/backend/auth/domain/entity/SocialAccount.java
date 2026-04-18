package com.backend.auth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity representing a linked social media account (OAuth2/OpenID Connect).
 * This allows a single internal User to authenticate via multiple external
 * providers.
 */
@Entity
@Table(name = "social_accounts", uniqueConstraints = @UniqueConstraint(name = "uq_social_provider_id", columnNames = {
        "provider", "provider_id" }), indexes = {
                @Index(name = "idx_social_user_id", columnList = "user_id"),
                @Index(name = "idx_social_provider_email", columnList = "email")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The internal user associated with this social account.
     * Uses FetchType.LAZY to prevent unnecessary joins during account lookups.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The external provider name (e.g., GOOGLE, FACEBOOK).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /**
     * The unique identifier provided by the social platform (the 'sub' or 'id'
     * claim).
     */
    @Column(name = "provider_id", nullable = false)
    private String providerId;

    /**
     * The email address associated with the social profile at the time of linking.
     */
    @Column(name = "email")
    private String email;

    /**
     * Timestamp of when this social account was first linked.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * Supported OAuth2 Identity Providers.
     */
    public enum Provider {
        GOOGLE,
        FACEBOOK,
        GITHUB // Adding more as an example for production scalability
    }
}