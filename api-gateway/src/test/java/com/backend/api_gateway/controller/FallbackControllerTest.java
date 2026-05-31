package com.backend.api_gateway.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.backend.api_gateway.dto.FallbackResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("FallbackController Tests")
class FallbackControllerTest {

    private FallbackController fallbackController;

    @BeforeEach
    void setUp() {
        fallbackController = new FallbackController();
    }

    @Nested
    @DisplayName("authFallback")
    class AuthFallbackTests {

        @Test
        @DisplayName("Should return auth service unavailable response")
        void authFallback_success() {
            // When
            var result = fallbackController.authFallback();

            // Then
            result.subscribe(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().success()).isFalse();
                assertThat(response.getBody().errorCode()).isEqualTo("AUTH_SERVICE_UNAVAILABLE");
                assertThat(response.getBody().message())
                        .isEqualTo("Auth service is temporarily unavailable. Please try again later.");
                assertThat(response.getBody().timestamp()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("chatFallback")
    class ChatFallbackTests {

        @Test
        @DisplayName("Should return chat service unavailable response")
        void chatFallback_success() {
            // When
            var result = fallbackController.chatFallback();

            // Then
            result.subscribe(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().success()).isFalse();
                assertThat(response.getBody().errorCode()).isEqualTo("CHAT_SERVICE_UNAVAILABLE");
                assertThat(response.getBody().message())
                        .isEqualTo("Chat service is temporarily unavailable. Please try again later.");
                assertThat(response.getBody().timestamp()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("diseasesFallback")
    class DiseasesFallbackTests {

        @Test
        @DisplayName("Should return disease data unavailable response")
        void diseasesFallback_success() {
            // When
            var result = fallbackController.diseasesFallback();

            // Then
            result.subscribe(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().success()).isFalse();
                assertThat(response.getBody().errorCode()).isEqualTo("DISEASE_DATA_UNAVAILABLE");
                assertThat(response.getBody().message())
                        .isEqualTo("Disease data is temporarily unavailable. Please check your cached data.");
                assertThat(response.getBody().timestamp()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("scanFallback")
    class ScanFallbackTests {

        @Test
        @DisplayName("Should return scan service unavailable response")
        void scanFallback_success() {
            // When
            var result = fallbackController.scanFallback();

            // Then
            result.subscribe(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().success()).isFalse();
                assertThat(response.getBody().errorCode()).isEqualTo("SCAN_SERVICE_UNAVAILABLE");
                assertThat(response.getBody().message())
                        .isEqualTo("Scan service is temporarily unavailable. Please try again later.");
                assertThat(response.getBody().timestamp()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("userFallback")
    class UserFallbackTests {

        @Test
        @DisplayName("Should return user service unavailable response")
        void userFallback_success() {
            // When
            var result = fallbackController.userFallback();

            // Then
            result.subscribe(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().success()).isFalse();
                assertThat(response.getBody().errorCode()).isEqualTo("USER_SERVICE_UNAVAILABLE");
                assertThat(response.getBody().message())
                        .isEqualTo("User service is temporarily unavailable. Please try again later.");
                assertThat(response.getBody().timestamp()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("communityFallback")
    class CommunityFallbackTests {

        @Test
        @DisplayName("Should return community service unavailable response")
        void communityFallback_success() {
            // When
            var result = fallbackController.communityFallback();

            // Then
            result.subscribe(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().success()).isFalse();
                assertThat(response.getBody().errorCode()).isEqualTo("COMMUNITY_SERVICE_UNAVAILABLE");
                assertThat(response.getBody().message())
                        .isEqualTo("Community service is temporarily unavailable. Please try again later.");
                assertThat(response.getBody().timestamp()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("searchFallback")
    class SearchFallbackTests {

        @Test
        @DisplayName("Should return search service unavailable response")
        void searchFallback_success() {
            // When
            var result = fallbackController.searchFallback();

            // Then
            result.subscribe(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().success()).isFalse();
                assertThat(response.getBody().errorCode()).isEqualTo("SEARCH_SERVICE_UNAVAILABLE");
                assertThat(response.getBody().message())
                        .isEqualTo("Search service is temporarily unavailable. Please try again later.");
                assertThat(response.getBody().timestamp()).isNotNull();
            });
        }
    }

    @Nested
    @DisplayName("notificationFallback")
    class NotificationFallbackTests {

        @Test
        @DisplayName("Should return notification service unavailable response")
        void notificationFallback_success() {
            // When
            var result = fallbackController.notificationFallback();

            // Then
            result.subscribe(response -> {
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().success()).isFalse();
                assertThat(response.getBody().errorCode()).isEqualTo("NOTIFICATION_SERVICE_UNAVAILABLE");
                assertThat(response.getBody().message())
                        .isEqualTo("Notification service is temporarily unavailable.");
                assertThat(response.getBody().timestamp()).isNotNull();
            });
        }
    }
}
