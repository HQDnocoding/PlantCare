package com.backend.auth.controller;

import com.backend.auth.aop.Idempotent;
import com.backend.auth.domain.dto.request.*;
import com.backend.auth.domain.dto.response.ApiResponse;
import com.backend.auth.domain.dto.response.AuthResponse;
import com.backend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login/social")
    @Idempotent(ttlSeconds = 300) // 5 minutes TTL for sensitive operations
    public ResponseEntity<ApiResponse<AuthResponse>> loginWithSocial(
            @Valid @RequestBody SocialLoginRequest request,
            HttpServletRequest httpRequest) {

        log.info("Social login attempt for provider: {}", request.getProvider());
        AuthResponse result = authService.loginWithSocial(request, getClientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/refresh")
    @Idempotent(ttlSeconds = 300) // 5 minutes TTL for token refresh
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse result = authService.refreshToken(request, getClientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PostMapping("/logout")
    @Idempotent(ttlSeconds = 300) // 5 minutes TTL for sensitive operations
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request) {

        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }

    @PostMapping("/logout/all")
    @Idempotent(ttlSeconds = 300) // 5 minutes TTL for sensitive operations
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal UUID userId) {

        authService.logoutAllDevices(userId);
        log.info("User {} performed global logout", userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out from all devices successfully"));
    }

    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<Map<String, String>>> guestLogin() {
        String token = authService.generateGuestToken();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("accessToken", token)));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2...
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}