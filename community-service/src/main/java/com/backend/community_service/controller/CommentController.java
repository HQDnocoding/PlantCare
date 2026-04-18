package com.backend.community_service.controller;

import com.backend.community_service.aop.Idempotent;
import com.backend.community_service.dto.ApiResponse;
import com.backend.community_service.dto.CommentRequest;
import com.backend.community_service.dto.CommentResponse;
import com.backend.community_service.dto.CursorPage;
import com.backend.community_service.service.CommentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@Validated
public class CommentController {

    private final CommentService commentService;

    // ── POST /posts/{postId}/comments ─────────────────────────────────────────

    @PostMapping("/api/v1/posts/{postId}/comments")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable UUID postId,
            @RequestBody @Valid CommentRequest request,
            Authentication auth) {

        UUID authorId = UUID.fromString(auth.getName());
        CommentResponse response = commentService.createComment(postId, authorId, request.getContent());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Comment created successfully"));
    }

    // ── GET /posts/{postId}/comments ──────────────────────────────────────────

    @GetMapping("/api/v1/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CursorPage<CommentResponse>>> getComments(
            @PathVariable UUID postId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        UUID requesterId = auth != null && !"anonymous".equals(auth.getName()) ? UUID.fromString(auth.getName()) : null;
        Instant cursorTime = cursor != null ? Instant.ofEpochMilli(Long.parseLong(cursor)) : null;

        CursorPage<CommentResponse> response = commentService.getComments(postId, cursorTime, size, requesterId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── POST /posts/{postId}/comments/{commentId}/replies ──────────────────────

    @PostMapping("/api/v1/posts/{postId}/comments/{commentId}/replies")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<CommentResponse>> createReply(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @Valid @RequestBody CommentRequest request,
            Authentication auth) {

        UUID authorId = UUID.fromString(auth.getName());
        CommentResponse response = commentService.createReply(postId, commentId, authorId, request.getContent());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Reply created successfully"));
    }

    // ── GET /posts/{postId}/comments/{commentId}/replies ───────────────────────

    @GetMapping("/api/v1/posts/{postId}/comments/{commentId}/replies")
    public ResponseEntity<ApiResponse<CursorPage<CommentResponse>>> getReplies(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        UUID requesterId = auth != null && !"anonymous".equals(auth.getName()) ? UUID.fromString(auth.getName()) : null;
        Instant cursorTime = cursor != null ? Instant.ofEpochMilli(Long.parseLong(cursor)) : null;

        CursorPage<CommentResponse> response = commentService.getReplies(commentId, cursorTime, size, requesterId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ── PUT /comments/{commentId} ─────────────────────────────────────────────

    @PutMapping("/api/v1/comments/{commentId}")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
            @PathVariable UUID commentId,
            @Valid @RequestBody CommentRequest request,
            Authentication auth) {

        UUID requesterId = UUID.fromString(auth.getName());
        CommentResponse response = commentService.updateComment(commentId, requesterId, request.getContent());

        return ResponseEntity.ok(ApiResponse.ok(response, "Comment updated successfully"));
    }

    // ── DELETE /comments/{commentId} ──────────────────────────────────────────

    @DeleteMapping("/api/v1/comments/{commentId}")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID commentId,
            Authentication auth) {

        UUID requesterId = UUID.fromString(auth.getName());
        commentService.deleteComment(commentId, requesterId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Comment deleted successfully")
                .build());
    }
}
