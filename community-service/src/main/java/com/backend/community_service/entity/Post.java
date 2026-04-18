package com.backend.community_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // PostgreSQL text[] — lưu Firebase Storage URL
    @Column(name = "image_urls", columnDefinition = "TEXT[]")
    @Builder.Default
    private String[] imageUrls = new String[0];

    // Firebase paths để xóa file khi post bị xóa
    @Column(name = "image_paths", columnDefinition = "TEXT[]")
    @Builder.Default
    private String[] imagePaths = new String[0];

    @Column(name = "upvote_count", nullable = false)
    @Builder.Default
    private int upvoteCount = 0;

    @Column(name = "downvote_count", nullable = false)
    @Builder.Default
    private int downvoteCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private int commentCount = 0;

    // Tags — one-to-many với post_tags table
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag")
    @Builder.Default
    private Set<String> tags = new HashSet<>();

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = Instant.now();
    }

    public int getScore() {
        return upvoteCount - downvoteCount;
    }

    /**
     * Hot score: (score) / (hours_since_posted + 2)^1.5
     * Dùng để sort feed theo độ hot
     */
    public double getHotScore() {
        double hoursSincePosted = (Instant.now().toEpochMilli() - createdAt.toEpochMilli())
                / (1000.0 * 3600.0);
        return getScore() / Math.pow(hoursSincePosted + 2, 1.5);
    }
}
