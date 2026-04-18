package com.backend.community_service.controller;

import com.backend.community_service.service.PostService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.backend.community_service.entity.Post;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
@Slf4j
public class InternalPostController {

    private final PostService postService;

    /** Called by admin-service to list all posts with pagination. */
    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("[Internal-Admin] GET /internal/v1/posts page={} size={}", page, size);
        Page<Post> result = postService.getAllPostsForAdmin(page, size);
        return ResponseEntity.ok(Map.of(
                "content",       result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages",    result.getTotalPages(),
                "page",          result.getNumber(),
                "size",          result.getSize()
        ));
    }

    /** Called by admin-service to fetch a single post by ID. */
    @GetMapping("/posts/{postId}")
    public ResponseEntity<Map<String, Object>> getPostById(@PathVariable UUID postId) {
        log.info("[Internal-Admin] GET /internal/v1/posts/{}", postId);
        return ResponseEntity.ok(postService.getAdminPostById(postId));
    }

    /** Called by admin-service to soft-delete a post. */
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<Map<String, Object>> deletePost(@PathVariable UUID postId) {
        log.info("[Internal-Admin] DELETE /internal/v1/posts/{}", postId);
        postService.deletePostByAdmin(postId);
        return ResponseEntity.ok(Map.of("message", "Post deleted", "postId", postId.toString()));
    }
}
