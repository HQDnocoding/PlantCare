package com.backend.auth.controller;

import com.backend.auth.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminUserService adminUserService;

    record LoginRequest(String email, String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body) {
        log.info("[Admin] POST /api/v1/admin/auth/login email={}", body.email());
        return ResponseEntity.ok(adminUserService.adminLogin(body.email(), body.password()));
    }
}
