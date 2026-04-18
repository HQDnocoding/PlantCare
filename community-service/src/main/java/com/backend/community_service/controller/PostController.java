package com.backend.community_service.controller;

import com.backend.community_service.aop.Idempotent;
import com.backend.community_service.client.UserServiceClient;
import com.backend.community_service.dto.ApiResponse;
import com.backend.community_service.dto.CursorPage;
import com.backend.community_service.dto.PostCreateRequest;
import com.backend.community_service.dto.PostResponse;
import com.backend.community_service.service.PostService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Slf4j
@Validated
public class PostController {

    private final PostService postService;
    private final UserServiceClient userServiceClient;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CursorPage<PostResponse>>> getMyPosts(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        CursorPage<PostResponse> response = postService.getMyPosts(userId, cursor, size);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<CursorPage<PostResponse>>> getPostsByUser(
            @PathVariable UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {

        CursorPage<PostResponse> response = postService.getMyPosts(userId, cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            Authentication auth,
            @Valid @ModelAttribute PostCreateRequest request) { // Dùng ModelAttribute

        UUID authorId = UUID.fromString(auth.getName());
        // request.getImages() sẽ chứa danh sách file
        PostResponse response = postService.createPost(authorId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Post created successfully"));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(
            @PathVariable UUID postId,
            Authentication auth) {

        UUID requesterId = (auth != null && !"anonymousUser".equals(auth.getName()) && !"GUEST".equals(auth.getName()))
                ? UUID.fromString(auth.getName())
                : null;
        PostResponse response = postService.getPost(postId, requesterId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/feed/global/new")
    public ResponseEntity<ApiResponse<CursorPage<PostResponse>>> getGlobalFeedNew(
            @RequestParam(required = false) String cursor, // Để là String
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {

        UUID requesterId = (auth != null && !"anonymousUser".equals(auth.getName()) && !"GUEST".equals(auth.getName()))
                ? UUID.fromString(auth.getName())
                : null;

        // Đẩy việc parse String -> Instant vào trong Service
        CursorPage<PostResponse> response = postService.getGlobalFeedNew(cursor, size, requesterId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/feed/global/top")
    public ResponseEntity<ApiResponse<CursorPage<PostResponse>>> getGlobalFeedTop(
            @RequestParam(required = false) Integer scoreCursor,
            @RequestParam(required = false) String timeCursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        UUID requesterId = (auth != null && !"anonymousUser".equals(auth.getName()) && !"GUEST".equals(auth.getName()))
                ? UUID.fromString(auth.getName())
                : null;
        Instant timeCursorInstant = timeCursor != null ? Instant.ofEpochMilli(Long.parseLong(timeCursor)) : null;

        CursorPage<PostResponse> response = postService.getGlobalFeedTop(scoreCursor, timeCursorInstant, size,
                requesterId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/feed/following")
    public ResponseEntity<ApiResponse<CursorPage<PostResponse>>> getFollowingFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        if (auth == null || "anonymousUser".equals(auth.getName()) || "GUEST".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<CursorPage<PostResponse>>builder()
                            .message("Authentication required")
                            .build());
        }

        UUID requesterId = UUID.fromString(auth.getName());

        List<UUID> followingIds = userServiceClient.getFollowingIds(requesterId);

        if (followingIds == null || followingIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(CursorPage.empty()));
        }
        Instant cursorTime = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(cursor));
                cursorTime = Instant.ofEpochMilli(Long.parseLong(decoded));
            } catch (Exception e) {
                log.warn("Invalid cursor format from client: {}", cursor);
            }
        }

        CursorPage<PostResponse> response = postService.getFollowingFeed(followingIds, cursorTime, size, requesterId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/tag/{tag}")
    public ResponseEntity<ApiResponse<CursorPage<PostResponse>>> getPostsByTag(
            @PathVariable String tag,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        UUID requesterId = (auth != null && !"anonymousUser".equals(auth.getName()) && !"GUEST".equals(auth.getName()))
                ? UUID.fromString(auth.getName())
                : null;
        Instant cursorTime = cursor != null ? Instant.ofEpochMilli(Long.parseLong(cursor)) : null;

        CursorPage<PostResponse> response = postService.getPostsByTag(tag, cursorTime, size, requesterId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{postId}")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable UUID postId,
            Authentication auth,
            // Fix Bug #6: dùng @RequestParam với @NotBlank — phải có @Validated ở
            // class-level
            // (mới thực sự validate; @Valid chỉ validate @RequestBody/@ModelAttribute).
            @RequestParam @jakarta.validation.constraints.NotBlank(message = "Nội dung không được để trống") @jakarta.validation.constraints.Size(max = 10000, message = "Nội dung quá dài") String content,
            @RequestParam(required = false) List<String> tags) {

        UUID requesterId = UUID.fromString(auth.getName());
        PostResponse response = postService.updatePost(postId, requesterId, content, tags);

        return ResponseEntity.ok(ApiResponse.ok(response, "Post updated successfully"));
    }

    @DeleteMapping("/{postId}")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable UUID postId,
            Authentication auth) {

        UUID requesterId = UUID.fromString(auth.getName());
        postService.deletePost(postId, requesterId);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("Post deleted successfully")
                .build());
    }

}
