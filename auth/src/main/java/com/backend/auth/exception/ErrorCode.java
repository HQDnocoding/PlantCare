package com.backend.auth.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// Single source of truth for all error codes.
// Each code has:
//   - name()       → machine-readable string sent to client (e.g. "INVALID_CREDENTIALS")
//   - httpStatus   → which HTTP status code to return
//   - message      → default human-readable message (can be overridden in AppException)
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

        // --- Registration errors ---
        EMAIL_ALREADY_EXISTS(
                        HttpStatus.CONFLICT,
                        "An account with this email already exists."),
        USERNAME_ALREADY_EXISTS(
                        HttpStatus.CONFLICT,
                        "An account with this username already exists."),
        REGISTRATION_ORCHESTRATION_FAILED(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Registration could not be completed. Please try again."),

        // --- Login errors ---
        INVALID_CREDENTIALS(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password."),
        ACCOUNT_NOT_VERIFIED(
                        HttpStatus.FORBIDDEN,
                        "Account username is not verified yet."),
        ACCOUNT_BLOCKED(
                        HttpStatus.FORBIDDEN,
                        "This account has been blocked. Please contact support."),
        ACCOUNT_NOT_FOUND(
                        HttpStatus.NOT_FOUND,
                        "Account not found."),
        ADMIN_NOT_FOUND(
                        HttpStatus.NOT_FOUND,
                        "Admin not found."),

        // --- Token errors ---
        REFRESH_TOKEN_INVALID(
                        HttpStatus.UNAUTHORIZED,
                        "Refresh token is invalid or expired."),
        ACCESS_TOKEN_INVALID(
                        HttpStatus.UNAUTHORIZED,
                        "Access token is invalid or expired."),

        // --- Social login errors ---
        SOCIAL_TOKEN_INVALID(
                        HttpStatus.UNAUTHORIZED,
                        "Social login token could not be verified."),
        SOCIAL_PROVIDER_NOT_SUPPORTED(
                        HttpStatus.BAD_REQUEST,
                        "Social login provider is not supported."),

        // --- Idempotency errors ---
        IDEMPOTENCY_KEY_CONFLICT(
                        HttpStatus.CONFLICT,
                        "Request body does not match the original request for this idempotency key."),

        // --- Generic errors ---
        INTERNAL_SERVER_ERROR(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred. Please try again later.");

        private final HttpStatus httpStatus;
        private final String message;
}
