package com.backend.community_service.repository;

import com.backend.community_service.entity.Comment;
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

public interface CommentRepository extends JpaRepository<Comment, UUID> {

  Optional<Comment> findByIdAndIsDeletedFalse(UUID id);

  // ── Pessimistic lock for vote operations (prevents concurrent vote races) ─
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT c FROM Comment c WHERE c.id = :id AND c.isDeleted = FALSE")
  Optional<Comment> findByIdAndIsDeletedFalseForUpdate(@Param("id") UUID id);

  // Top-level comments của 1 post (cursor-based, sort by new)
  @Query("""
      SELECT c FROM Comment c
      WHERE c.postId = :postId
        AND c.parentId IS NULL
        AND c.isDeleted = FALSE
        AND (cast(:cursor as instant) IS NULL OR c.createdAt < :cursor)
      ORDER BY c.createdAt DESC
      """)
  List<Comment> findTopLevelByPostId(
      @Param("postId") UUID postId,
      @Param("cursor") Instant cursor,
      Pageable pageable);

  // Replies của 1 comment (cursor-based)
  @Query("""
      SELECT c FROM Comment c
      WHERE c.parentId = :parentId
        AND c.isDeleted = FALSE
        AND (cast(:cursor as instant) IS NULL OR c.createdAt < :cursor)
      ORDER BY c.createdAt ASC
      """)
  List<Comment> findRepliesByParentId(
      @Param("parentId") UUID parentId,
      @Param("cursor") Instant cursor,
      Pageable pageable);

  // Kiểm tra comment có thuộc post không (tránh reply cross-post)
  boolean existsByIdAndPostId(UUID id, UUID postId);

  @Query("SELECT c.authorId FROM Comment c WHERE c.id = :commentId AND c.isDeleted = false")
  Optional<UUID> findAuthorIdById(@Param("commentId") UUID commentId);
}
