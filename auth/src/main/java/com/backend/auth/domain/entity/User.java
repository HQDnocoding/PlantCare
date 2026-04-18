package com.backend.auth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core User entity representing identity and profile information.
 * Supports soft deletion, account status management, and links to
 * social accounts and refresh tokens.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_phone", columnList = "phone"),
        @Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_status", columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique phone number for authentication and OTP.
     */
    @Column(unique = true)
    private String phone;

    /**
     * User's email address. Not necessarily unique if multiple social accounts
     * are linked, but usually managed at the business logic level.
     */
    @Column(unique = false)
    private String email;

    /**
     * BCrypt or Argon2 hashed password. Nullable for social-only accounts.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    /**
     * Primary security role of the user.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.FARMER;

    /**
     * Current lifecycle state of the user account.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.UNVERIFIED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Timestamp for soft deletion. If not null, the user is considered deleted.
     */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /**
     * List of linked social media accounts (Google, Facebook, etc.)
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<SocialAccount> socialAccounts = new ArrayList<>();

    /**
     * Active refresh tokens for multi-device session management.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    // --- Helper Methods ---

    /**
     * Determines if the user is allowed to access the system.
     * 
     * @return true if status is ACTIVE and not deleted.
     */
    public boolean isActive() {
        return status == UserStatus.ACTIVE && deletedAt == null;
    }

    /**
     * Checks if the account has been soft-deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Checks if the user has a password set (False for social-only users).
     */
    public boolean hasPassword() {
        return passwordHash != null;
    }

    /**
     * Performs a soft delete by setting the deletedAt timestamp.
     */
    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
        this.status = UserStatus.BLOCKED; // Optional: change status upon deletion
    }

    public enum Role {
        FARMER,
        AGRONOMIST,
        ADMIN
    }

    public enum UserStatus {
        UNVERIFIED, // Pending OTP/Email verification
        ACTIVE, // Full access
        BLOCKED // Banned by admin
    }
}