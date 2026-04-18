package com.backend.scan_service.controller;

import com.backend.scan_service.aop.Idempotent;
import com.backend.scan_service.dto.*;
import com.backend.scan_service.exception.*;
import com.backend.scan_service.services.ScanService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/v1/scans")
@RequiredArgsConstructor
@Slf4j
public class ScanController {

    private final ScanService scanService;

    private UUID getUserId(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null)
            throw new AppException(ErrorCode.UNAUTHORIZED);
        return UUID.fromString(userId);
    }

    @PostMapping
    @Idempotent(ttlSeconds = 3600) // Cache 1 hour for image uploads
    @RateLimiter(name = "scanRateLimiter", fallbackMethod = "scanRateLimitFallback")
    public ResponseEntity<ApiResponse<ScanResponse>> save(
            @RequestParam("image") MultipartFile image,
            @RequestParam("disease") String disease,
            @RequestParam("confidence") Double confidence,
            @RequestParam("confidentEnough") Boolean confidentEnough,
            HttpServletRequest request) {

        log.debug("POST /api/v1/scans - disease={}, confidence={}", disease, confidence);

        SaveScanRequest body = new SaveScanRequest();
        body.setImage(image);
        body.setDisease(disease);
        body.setConfidence(confidence);
        body.setConfidentEnough(confidentEnough);

        return ResponseEntity.ok(ApiResponse.ok(
                scanService.save(getUserId(request), body)));
    }

    /**
     * Fallback method when rate limit is exceeded
     */
    public ResponseEntity<ApiResponse<ScanResponse>> scanRateLimitFallback(
            MultipartFile image, String disease, Double confidence, Boolean confidentEnough,
            HttpServletRequest request, Exception e) {
        log.warn("Scan rate limit exceeded for user: {}", request.getHeader("X-User-Id"));
        throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED,
                "Too many scan requests. Please try again later. Limit: 10 scans per minute");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScanResponse>>> getHistory(
            HttpServletRequest request) {
        List<ScanResponse> scans = scanService.getHistory(getUserId(request));
        log.debug("GET /api/v1/scans - retrieved {} scan records", scans.size());
        return ResponseEntity.ok(ApiResponse.ok(scans));
    }

    @Idempotent(ttlSeconds = 86400) // Cache 24 hours
    @PatchMapping("/{id}/conv")
    public ResponseEntity<ApiResponse<ScanResponse>> updateConv(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConvRequest body,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                scanService.updateConv(getUserId(request), id, body)));
    }

    @Idempotent(ttlSeconds = 3600) // Cache 1 hour
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            HttpServletRequest request) {
        scanService.delete(getUserId(request), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}