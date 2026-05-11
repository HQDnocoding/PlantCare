package com.backend.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.backend.notification_service.dto.NotificationResponse;
import com.backend.notification_service.entity.Notification;
import com.backend.notification_service.exception.AppException;
import com.backend.notification_service.exception.ErrorCode;
import com.backend.notification_service.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepo;

    @Mock
    private FcmService fcmService;

    @Mock
    private SseService sseService;

    @InjectMocks
    private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @Nested
    @DisplayName("createAndSend")
    class CreateAndSendTests {

        @Test
        @DisplayName("Should create and send notification successfully")
        void createAndSend_success() {
            // Given
            String type = "COMMENT";
            String title = "New comment";
            String body = "Someone commented on your post";
            String actorName = "John Doe";
            String targetType = "POST";

            Notification savedNotification = Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .body(body)
                    .actorId(actorId)
                    .actorName(actorName)
                    .targetId(targetId)
                    .targetType(targetType)
                    .isRead(false)
                    .createdAt(java.time.Instant.now())
                    .build();

            when(notificationRepo.save(any(Notification.class))).thenReturn(savedNotification);
            doNothing().when(sseService).sendToUser(eq(userId), any(NotificationResponse.class));
            doNothing().when(fcmService).sendToUser(eq(userId), eq(title), eq(body), any(Map.class));

            // When
            notificationService.createAndSend(userId, type, title, body, actorId, actorName, targetId, targetType);

            // Then
            ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepo).save(notificationCaptor.capture());

            Notification captured = notificationCaptor.getValue();
            assertThat(captured.getUserId()).isEqualTo(userId);
            assertThat(captured.getType()).isEqualTo(type);
            assertThat(captured.getTitle()).isEqualTo(title);
            assertThat(captured.getBody()).isEqualTo(body);
            assertThat(captured.getActorId()).isEqualTo(actorId);
            assertThat(captured.getActorName()).isEqualTo(actorName);
            assertThat(captured.getTargetId()).isEqualTo(targetId);
            assertThat(captured.getTargetType()).isEqualTo(targetType);
            assertThat(captured.isRead()).isFalse();

            verify(sseService).sendToUser(eq(userId), any(NotificationResponse.class));
            verify(fcmService).sendToUser(eq(userId), eq(title), eq(body), any(Map.class));
        }

        @Test
        @DisplayName("Should throw UNAUTHORIZED when userId is null")
        void createAndSend_nullUserId() {
            // When & Then
            assertThatThrownBy(() -> notificationService.createAndSend(null, "COMMENT", "title", "body", actorId,
                    "actor", targetId, "POST"))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

            verify(notificationRepo, never()).save(any());
            verify(sseService, never()).sendToUser(any(), any());
            verify(fcmService, never()).sendToUser(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should continue when FCM fails but not throw exception")
        void createAndSend_fcmFails() {
            // Given
            Notification savedNotification = Notification.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .type("COMMENT")
                    .title("New comment")
                    .body("Someone commented")
                    .actorId(actorId)
                    .actorName("John")
                    .targetId(targetId)
                    .targetType("POST")
                    .isRead(false)
                    .createdAt(java.time.Instant.now())
                    .build();

            when(notificationRepo.save(any(Notification.class))).thenReturn(savedNotification);
            doNothing().when(sseService).sendToUser(eq(userId), any(NotificationResponse.class));
            doThrow(new AppException(ErrorCode.INTERNAL_SERVER_ERROR)).when(fcmService)
                    .sendToUser(eq(userId), any(), any(), any());

            // When
            notificationService.createAndSend(userId, "COMMENT", "New comment", "Someone commented", actorId, "John",
                    targetId, "POST");

            // Then
            verify(notificationRepo).save(any());
            verify(sseService).sendToUser(eq(userId), any());
            verify(fcmService).sendToUser(eq(userId), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getNotifications")
    class GetNotificationsTests {

        @Test
        @DisplayName("Should return paginated notifications")
        void getNotifications_success() {
            // Given
            int page = 0;
            int size = 10;
            PageRequest pageable = PageRequest.of(page, size);

            List<Notification> notifications = List.of(
                    createNotification("COMMENT", "Comment 1"),
                    createNotification("VOTE", "Vote 1"));

            when(notificationRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(notifications);

            // When
            List<NotificationResponse> result = notificationService.getNotifications(userId, page, size);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getType()).isEqualTo("COMMENT");
            assertThat(result.get(1).getType()).isEqualTo("VOTE");

            verify(notificationRepo).findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        @Test
        @DisplayName("Should return empty list when no notifications")
        void getNotifications_empty() {
            // Given
            int page = 0;
            int size = 10;
            PageRequest pageable = PageRequest.of(page, size);
            List<Notification> emptyList = List.of();

            when(notificationRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(emptyList);

            // When
            List<NotificationResponse> result = notificationService.getNotifications(userId, page, size);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCountTests {

        @Test
        @DisplayName("Should return unread count")
        void getUnreadCount_success() {
            // Given
            when(notificationRepo.countByUserIdAndIsReadFalse(userId)).thenReturn(5L);

            // When
            long result = notificationService.getUnreadCount(userId);

            // Then
            assertThat(result).isEqualTo(5L);
            verify(notificationRepo).countByUserIdAndIsReadFalse(userId);
        }

        @Test
        @DisplayName("Should return zero when no unread notifications")
        void getUnreadCount_zero() {
            // Given
            when(notificationRepo.countByUserIdAndIsReadFalse(userId)).thenReturn(0L);

            // When
            long result = notificationService.getUnreadCount(userId);

            // Then
            assertThat(result).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("markAllAsRead")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("Should mark all notifications as read")
        void markAllAsRead_success() {
            // When
            notificationService.markAllAsRead(userId);

            // Then
            verify(notificationRepo).markAllAsRead(userId);
        }
    }

    private Notification createNotification(String type, String body) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type(type)
                .title("Test Title")
                .body(body)
                .actorId(actorId)
                .actorName("Test Actor")
                .targetId(targetId)
                .targetType("POST")
                .isRead(false)
                .createdAt(java.time.Instant.now())
                .build();
    }
}