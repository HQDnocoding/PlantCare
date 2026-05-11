package com.backend.community_service.controller;

import com.backend.community_service.dto.ApiResponse;
import com.backend.community_service.entity.Comment;
import com.backend.community_service.service.CommentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/comments")
@RequiredArgsConstructor
@Slf4j
public class AdminCommentController {

    private final CommentService commentService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllComments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("[Admin] GET /api/v1/admin/comments page={} size={}", page, size);
        Page<Comment> result = commentService.getAllCommentsForAdmin(page, size);
        Map<String, Object> body = Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "size", result.getSize());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable UUID commentId) {
        log.info("[Admin] DELETE /api/v1/admin/comments/{}", commentId);
        commentService.deleteCommentByAdmin(commentId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Comment deleted successfully")
                .build());
    }
}
