package com.backend.notification_service.controller;

import com.backend.notification_service.service.FcmService;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.backend.notification_service.dto.ApiResponse;
import com.backend.notification_service.dto.NotificationResponse;
import com.backend.notification_service.service.NotificationService;
import com.backend.notification_service.service.SseService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final FcmService fcmService;
    private final NotificationService notificationService;
    private final SseService sseService;

    // SSE — app mở subscribe vào đây
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader("X-User-Id") UUID userId) {
        return sseService.subscribe(userId);
    }

    // Lấy danh sách notification
    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.getNotifications(userId, page, size)));
    }

    // Số notification chưa đọc
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                notificationService.getUnreadCount(userId)));
    }

    // Đánh dấu tất cả đã đọc
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            @RequestHeader("X-User-Id") UUID userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Marked all as read"));
    }

    // Đăng ký FCM token
    @PostMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> registerFcmToken(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam String token,
            @RequestParam(required = false) String deviceInfo) {

        fcmService.registerFcmToken(userId, token, deviceInfo);
        return ResponseEntity.ok(ApiResponse.ok(null, "FCM token registered"));
    }

    // Xóa FCM token khi logout
    @DeleteMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> removeFcmToken(
            @RequestParam String token) {
        fcmService.deleteByToken(token);
        return ResponseEntity.ok(ApiResponse.ok(null, "FCM token removed"));
    }
}