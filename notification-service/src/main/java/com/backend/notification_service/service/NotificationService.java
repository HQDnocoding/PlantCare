package com.backend.notification_service.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.notification_service.dto.NotificationResponse;
import com.backend.notification_service.entity.Notification;
import com.backend.notification_service.exception.AppException;
import com.backend.notification_service.exception.ErrorCode;
import com.backend.notification_service.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final FcmService fcmService;
    private final SseService sseService;

    @Transactional
    public void createAndSend(UUID userId, String type, String title,
            String body, UUID actorId, String actorName,
            UUID targetId, String targetType) {
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        // 1. Lưu DB
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .actorId(actorId)
                .actorName(actorName)
                .targetId(targetId)
                .targetType(targetType)
                .isRead(false)
                .build();
        notificationRepo.save(notification);

        NotificationResponse response = toResponse(notification);

        // 2. SSE — nếu app đang mở
        sseService.sendToUser(userId, response);

        // 3. FCM — nếu app đang đóng
        Map<String, String> fcmData = new HashMap<>();
        fcmData.put("type", type != null ? type : "");
        fcmData.put("targetId", targetId != null ? targetId.toString() : "");
        fcmData.put("targetType", targetType != null ? targetType : "");

        try {
            fcmService.sendToUser(userId, title, body, fcmData);
        } catch (AppException e) {
            log.warn("FCM delivery failed for userId={}: {}", userId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepo.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepo.markAllAsRead(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .actorId(n.getActorId())
                .actorName(n.getActorName())
                .targetId(n.getTargetId())
                .targetType(n.getTargetType())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}