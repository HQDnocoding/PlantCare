package com.backend.auth.controller;

import com.backend.auth.domain.dto.response.AdminUserResponse;
import com.backend.auth.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

record StatusRequest(String status) {
}

record RoleRequest(String role) {
}

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("[Admin] GET /api/v1/admin/users page={} size={}", page, size);
        Page<AdminUserResponse> result = adminUserService.getAllUsers(page, size);
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "size", result.getSize()));
    }

    @PatchMapping("/{userId}/status")
    @com.backend.auth.aop.Idempotent(ttlSeconds = 300)
    public ResponseEntity<AdminUserResponse> updateStatus(
            @PathVariable UUID userId,
            @RequestBody StatusRequest body) {
        log.info("[Admin] PATCH /api/v1/admin/users/{}/status → {}", userId, body.status());
        return ResponseEntity.ok(adminUserService.updateStatus(userId, body.status()));
    }

    @PatchMapping("/{userId}/role")
    @com.backend.auth.aop.Idempotent(ttlSeconds = 300)
    public ResponseEntity<AdminUserResponse> updateRole(
            @PathVariable UUID userId,
            @RequestBody RoleRequest body) {
        log.info("[Admin] PATCH /api/v1/admin/users/{}/role → {}", userId, body.role());
        return ResponseEntity.ok(adminUserService.updateRole(userId, body.role()));
    }
}
