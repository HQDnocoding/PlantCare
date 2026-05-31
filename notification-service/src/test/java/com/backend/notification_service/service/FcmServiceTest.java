package com.backend.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.notification_service.entity.FcmToken;
import com.backend.notification_service.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmService Tests")
class FcmServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepo;

    @InjectMocks
    private FcmService fcmService;

    private final UUID userId = UUID.randomUUID();
    private final String token = "test-fcm-token";
    private final String title = "Test Title";
    private final String body = "Test Body";
    private final Map<String, String> data = Map.of("type", "COMMENT", "targetId", "123");

    @Nested
    @DisplayName("sendToUser")
    class SendToUserTests {

        @Test
        @DisplayName("Should send to all user tokens successfully")
        void sendToUser_success() throws FirebaseMessagingException {
            // Given
            List<FcmToken> tokens = List.of(
                    createFcmToken("token1"),
                    createFcmToken("token2"));

            when(fcmTokenRepo.findByUserId(userId)).thenReturn(tokens);

            try (MockedStatic<FirebaseMessaging> mockedFirebase = org.mockito.Mockito
                    .mockStatic(FirebaseMessaging.class)) {
                var mockFirebaseMessaging = org.mockito.Mockito.mock(FirebaseMessaging.class);
                mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(mockFirebaseMessaging);
                when(mockFirebaseMessaging.send(any())).thenReturn("message-id");

                // When
                fcmService.sendToUser(userId, title, body, data);

                // Then
                verify(fcmTokenRepo).findByUserId(userId);
                verify(mockFirebaseMessaging, org.mockito.Mockito.times(2)).send(any());
            }
        }

        @Test
        @DisplayName("Should do nothing when no tokens found")
        void sendToUser_noTokens() {
            // Given
            when(fcmTokenRepo.findByUserId(userId)).thenReturn(List.of());

            // When
            fcmService.sendToUser(userId, title, body, data);

            // Then
            verify(fcmTokenRepo).findByUserId(userId);
        }

        @Test
        @DisplayName("Should remove expired tokens")
        void sendToUser_expiredToken() throws FirebaseMessagingException {
            // Given
            List<FcmToken> tokens = List.of(createFcmToken("expired-token"));

            when(fcmTokenRepo.findByUserId(userId)).thenReturn(tokens);

            try (MockedStatic<FirebaseMessaging> mockedFirebase = org.mockito.Mockito
                    .mockStatic(FirebaseMessaging.class)) {
                var mockFirebaseMessaging = org.mockito.Mockito.mock(FirebaseMessaging.class);
                mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(mockFirebaseMessaging);

                var expiredException = org.mockito.Mockito.mock(FirebaseMessagingException.class);
                when(expiredException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
                when(mockFirebaseMessaging.send(any())).thenThrow(expiredException);

                // When
                fcmService.sendToUser(userId, title, body, data);

                // Then
                verify(fcmTokenRepo).deleteByToken("expired-token");
            }
        }
    }

    @Nested
    @DisplayName("registerFcmToken")
    class RegisterFcmTokenTests {

        @Test
        @DisplayName("Should register FCM token")
        void registerFcmToken_success() {
            // Given
            String deviceInfo = "iPhone 12";

            // When
            fcmService.registerFcmToken(userId, token, deviceInfo);

            // Then
            verify(fcmTokenRepo).upsertToken(userId, token, deviceInfo);
        }
    }

    @Nested
    @DisplayName("deleteByToken")
    class DeleteByTokenTests {

        @Test
        @DisplayName("Should delete FCM token")
        void deleteByToken_success() {
            // When
            fcmService.deleteByToken(token);

            // Then
            verify(fcmTokenRepo).deleteByToken(token);
        }
    }

    private FcmToken createFcmToken(String token) {
        return FcmToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .token(token)
                .deviceInfo("Test Device")
                .createdAt(java.time.Instant.now())
                .build();
    }
}
