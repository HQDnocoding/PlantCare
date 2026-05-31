package com.backend.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.backend.notification_service.dto.NotificationResponse;
import com.backend.notification_service.exception.AppException;
import com.backend.notification_service.exception.ErrorCode;

@DisplayName("SseService Tests")
class SseServiceTest {

    private SseService sseService;

    private final UUID userId = UUID.randomUUID();
    private final NotificationResponse notification = NotificationResponse.builder()
            .id(UUID.randomUUID())
            .type("COMMENT")
            .title("New Comment")
            .body("Someone commented")
            .actorId(UUID.randomUUID())
            .actorName("John")
            .targetId(UUID.randomUUID())
            .targetType("POST")
            .isRead(false)
            .createdAt(java.time.Instant.now())
            .build();

    @BeforeEach
    void setUp() {
        sseService = new SseService();
    }

    @Nested
    @DisplayName("subscribe")
    class SubscribeTests {

        @Test
        @DisplayName("Should create and return SSE emitter for valid user")
        void subscribe_success() {
            // When
            var result = sseService.subscribe(userId);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should throw UNAUTHORIZED when userId is null")
        void subscribe_nullUserId() {
            // When & Then
            assertThatThrownBy(() -> sseService.subscribe(null))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("sendToUser")
    class SendToUserTests {

        @Test
        @DisplayName("Should handle sending to user without emitters gracefully")
        void sendToUser_noEmitters() {
            // When & Then - Should not throw exception
            assertThatCode(() -> sseService.sendToUser(userId, notification)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Should handle sending to subscribed user")
        void sendToUser_withEmitter() {
            // Given - Subscribe an emitter
            var emitter = sseService.subscribe(userId);
            assertThat(emitter).isNotNull();

            // When & Then - Should not throw exception
            assertThatCode(() -> sseService.sendToUser(userId, notification)).doesNotThrowAnyException();
        }
    }
}