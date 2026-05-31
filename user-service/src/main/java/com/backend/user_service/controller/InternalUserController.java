package com.backend.user_service.controller;

import com.backend.user_service.dto.ApiResponse;
import com.backend.user_service.dto.CreateProfileRequest;
import com.backend.user_service.dto.UserProfileResponse;
import com.backend.user_service.entity.UserProfile;
import com.backend.user_service.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserProfileResponse> getProfileInternal(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getProfileInternal(userId));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserProfileResponse>> createProfile(
            @Valid @RequestBody CreateProfileRequest req) {
        log.info("[Internal] createProfile | userId={} displayName={}", req.getUserId(), req.getDisplayName());
        UserProfileResponse profile = userService.createProfile(req.getUserId(), req.getDisplayName(),
                req.getAvatarUrl());
        return ResponseEntity.ok(ApiResponse.<UserProfileResponse>builder()
                .message("Profile created")
                .data(profile)
                .build());
    }

    @GetMapping("/users/{userId}/following-ids")
    public List<UUID> getFollowingIds(@PathVariable UUID userId) {
        return userService.getFollowingIds(userId);
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("[Internal-Admin] GET /internal/v1/users page={} size={}", page, size);
        Page<UserProfile> result = userService.getAllUsers(page, size);
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "size", result.getSize()
        ));
    }

    @GetMapping("/users/batch")
    public ResponseEntity<Map<UUID, UserProfileResponse>> getProfilesBatch(
            @RequestParam List<UUID> ids) {
        return ResponseEntity.ok(userService.getProfilesBatch(ids));
    }
}