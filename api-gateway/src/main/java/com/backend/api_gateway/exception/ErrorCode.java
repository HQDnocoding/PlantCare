package com.backend.api_gateway.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

        // --- Token ---
        ACCESS_TOKEN_INVALID(
                        HttpStatus.UNAUTHORIZED,
                        "Access token is invalid or expired."),
        ACCESS_TOKEN_MISSING(
                        HttpStatus.UNAUTHORIZED,
                        "Access token is missing."),

        // --- Authorization ---
        FORBIDDEN(
                        HttpStatus.FORBIDDEN,
                        "You do not have permission to access this resource."),

        // --- Generic ---
        INTERNAL_SERVER_ERROR(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred. Please try again later.");

        private final HttpStatus httpStatus;
        private final String message;
}