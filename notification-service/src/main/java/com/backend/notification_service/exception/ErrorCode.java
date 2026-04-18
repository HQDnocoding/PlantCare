package com.backend.notification_service.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // --- Generic ---
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later."),
    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized."),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "Forbidden."),

    // --- Notification ---
    NOTIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Notification not found."),

    // --- FCM ---
    FCM_TOKEN_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FCM token not found."),
    FCM_SEND_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to send push notification.");

    private final HttpStatus httpStatus;
    private final String message;
}