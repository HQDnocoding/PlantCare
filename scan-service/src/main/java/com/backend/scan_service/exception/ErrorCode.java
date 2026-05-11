package com.backend.scan_service.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    RATE_LIMIT_EXCEEDED(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests. Please try again later."),

    // --- Scan errors ---
    SCAN_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "Scan not found."),

    // --- Auth errors ---
    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized."),

    // --- Generic errors ---
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Wrong input.");

    private final HttpStatus httpStatus;
    private final String message;
}