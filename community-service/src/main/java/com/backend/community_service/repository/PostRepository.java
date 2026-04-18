package com.backend.community_service.repository;

import com.backend.community_service.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

        Optional<Post> findByIdAndIsDeletedFalse(UUID id);

        // ── Pessimistic lock for vote operations (prevents concurrent vote races) ─
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Post p WHERE p.id = :id AND p.isDeleted = FALSE")
        Optional<Post> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

        // ── Global feed: New (cursor = createdAt của item cuối trang trước) ───────
        @Query("""
                        SELECT p FROM Post p
                        WHERE p.isDeleted = FALSE
                          AND (cast(:cursor as instant) IS NULL OR p.createdAt < :cursor)
                        ORDER BY p.createdAt DESC
                        """)
        List<Post> findGlobalFeedNew(
                        @Param("cursor") Instant cursor,
                        Pageable pageable);

        // ── Global feed: Top (theo score = upvote - downvote) ────────────────────
        @Query("""
                        SELECT p FROM Post p
                        WHERE p.isDeleted = FALSE
                          AND (cast(:cursor as instant) IS NULL OR (p.upvoteCount - p.downvoteCount) < :scoreCursor
                               OR ((p.upvoteCount - p.downvoteCount) = :scoreCursor AND p.createdAt < :cursor))
                        ORDER BY (p.upvoteCount - p.downvoteCount) DESC, p.createdAt DESC
                        """)
        List<Post> findGlobalFeedTop(
                        @Param("scoreCursor") Integer scoreCursor,
                        @Param("cursor") Instant cursor,
                        Pageable pageable);

        // ── Following feed: chỉ lấy bài của những user mà requesterId đang follow ─
        // authorIds được truyền vào từ service (lấy từ user-service hoặc cache)
        @Query("""
                        SELECT p FROM Post p
                        WHERE p.isDeleted = FALSE
                          AND p.authorId IN :authorIds
                          AND (cast(:cursor as instant) IS NULL OR p.createdAt < :cursor)
                        ORDER BY p.createdAt DESC
                        """)
        List<Post> findFollowingFeed(
                        @Param("authorIds") List<UUID> authorIds,
                        @Param("cursor") Instant cursor,
                        Pageable pageable);

        // ── Filter theo tag ───────────────────────────────────────────────────────
        @Query("""
                        SELECT DISTINCT p FROM Post p
                        JOIN p.tags t
                        WHERE p.isDeleted = FALSE
                          AND lower(t) = lower(:tag)
                          AND (cast(:cursor as instant) IS NULL OR p.createdAt < :cursor)
                        ORDER BY p.createdAt DESC
                        """)
        List<Post> findByTag(
                        @Param("tag") String tag,
                        @Param("cursor") Instant cursor,
                        Pageable pageable);

        // ── Posts của 1 user (cho profile page) ──────────────────────────────────
        @Query("""
                        SELECT p FROM Post p
                        WHERE p.isDeleted = FALSE
                          AND p.authorId = :authorId
                          AND (cast(:cursor as instant) IS NULL OR p.createdAt < :cursor)
                        ORDER BY p.createdAt DESC
                        """)
        List<Post> findByAuthorId(
                        @Param("authorId") UUID authorId,
                        @Param("cursor") Instant cursor,
                        Pageable pageable);

        @Query("SELECT p FROM Post p WHERE p.authorId = :userId AND p.isDeleted = false")
        List<Post> findAllActiveByAuthorId(@Param("userId") UUID userId);

        @Query("SELECT p.authorId FROM Post p WHERE p.id = :postId AND p.isDeleted = false")
        Optional<UUID> findAuthorIdById(@Param("postId") UUID postId);

}
