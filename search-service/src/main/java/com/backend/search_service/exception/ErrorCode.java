package com.backend.search_service.exception;

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

    // --- Search ---
    INVALID_SEARCH_QUERY(
            HttpStatus.BAD_REQUEST,
            "Search query must not be empty."),
    SEARCH_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Search service is temporarily unavailable."),

    // --- Index ---
    INDEX_NOT_FOUND(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Search index is not ready yet."),
    INDEX_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to index document.");

    private final HttpStatus httpStatus;
    private final String message;
}