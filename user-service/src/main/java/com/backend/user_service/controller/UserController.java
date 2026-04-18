package com.backend.user_service.controller;

import com.backend.user_service.aop.Idempotent;
import com.backend.user_service.dto.ApiResponse;
import com.backend.user_service.dto.UpdateProfileRequest;
import com.backend.user_service.dto.UserProfileResponse;
import com.backend.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ─── Public endpoints ────────────────────────────────────────────────────

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UUID requesterId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId, requesterId)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getProfile(userId, userId)));
    }

    @PutMapping("/me/profile")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateProfile(userId, req), "Profile updated"));
    }

    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateAvatar(
            @AuthenticationPrincipal UUID userId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateAvatar(userId, file), "Avatar updated"));
    }

    @DeleteMapping("/me")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal UUID userId) {
        userService.deleteAccount(userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Account deleted"));
    }

    // ─── Follow ──────────────────────────────────────────────────────────────

    @PostMapping("/{targetId}/follow")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<Void>> follow(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID targetId) {
        userService.follow(userId, targetId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Followed"));
    }

    @DeleteMapping("/{targetId}/follow")
    @Idempotent(ttlSeconds = 86400)
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID targetId) {
        userService.unfollow(userId, targetId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Unfollowed"));
    }

}
