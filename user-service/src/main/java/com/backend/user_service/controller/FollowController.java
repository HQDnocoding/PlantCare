package com.backend.user_service.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.user_service.dto.ApiResponse;
import com.backend.user_service.dto.UserProfileResponse;
import com.backend.user_service.service.FollowService;
import com.backend.user_service.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final UserService userService;

    // ── GET /me/followers (Ai đang follow tôi?) ──────────────────────────────
    @GetMapping("/me/followers")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getMyFollowers(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        List<UserProfileResponse> followers = followService.getFollowers(userId);
        return ResponseEntity.ok(ApiResponse.ok(followers));
    }

    // ── GET /me/following (Tôi đang follow ai?) ──────────────────────────────
    @GetMapping("/me/following")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> getMyFollowing(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        List<UserProfileResponse> following = followService.getFollowing(userId);
        return ResponseEntity.ok(ApiResponse.ok(following));
    }

    // ── DELETE /following/{targetId} (Hủy follow người khác) ─────────────────
    @DeleteMapping("/following/{targetId}")
    public ResponseEntity<ApiResponse<Void>> unfollow(@PathVariable UUID targetId, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        userService.unfollow(userId, targetId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Unfollowed successfully"));
    }

    // ── DELETE /me/followers/{followerId} (Xóa người đang follow mình) ──────────
    @DeleteMapping("/me/followers/{followerId}")
    public ResponseEntity<ApiResponse<Void>> removeFollower(
            @PathVariable UUID followerId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        userService.unfollow(followerId, userId); // followerId unfollow userId
        return ResponseEntity.ok(ApiResponse.ok(null, "Follower removed"));
    }
}