package com.backend.community_service.controller;

import com.backend.community_service.dto.ApiResponse;
import com.backend.community_service.dto.PostResponse;
import com.backend.community_service.entity.Post;
import com.backend.community_service.service.PostService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
@Slf4j
@Validated
public class AdminPostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("[Admin] GET /api/v1/admin/posts page={} size={}", page, size);
        Page<Post> result = postService.getAllPostsForAdmin(page, size);
        Map<String, Object> body = Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "size", result.getSize());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @Data
    static class UpdatePostRequest {
        @NotBlank(message = "Nội dung không được để trống")
        @Size(max = 10000, message = "Nội dung quá dài")
        private String content;
        private List<String> tags;
    }

    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable UUID postId,
            @RequestBody @Valid UpdatePostRequest body) {
        log.info("[Admin] PATCH /api/v1/admin/posts/{}", postId);
        PostResponse response = postService.updatePostByAdmin(postId, body.getContent(), body.getTags());
        return ResponseEntity.ok(ApiResponse.ok(response, "Post updated successfully"));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable UUID postId) {
        log.info("[Admin] DELETE /api/v1/admin/posts/{}", postId);
        postService.deletePostByAdmin(postId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Post deleted successfully")
                .build());
    }
}
