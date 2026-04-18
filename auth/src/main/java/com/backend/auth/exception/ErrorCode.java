package com.backend.auth.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// Single source of truth for all error codes.
// Each code has:
//   - name()       → machine-readable string sent to client (e.g. "OTP_EXPIRED")
//   - httpStatus   → which HTTP status code to return
//   - message      → default human-readable message (can be overridden in AppException)
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

        // --- OTP errors ---
        OTP_SEND_RATE_LIMIT_EXCEEDED(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many OTP requests. Please wait before requesting again."),
        OTP_NOT_FOUND(
                        HttpStatus.BAD_REQUEST,
                        "No valid OTP found for this phone number."),
        OTP_EXPIRED(
                        HttpStatus.BAD_REQUEST,
                        "OTP has expired. Please request a new one."),
        OTP_INVALID(
                        HttpStatus.BAD_REQUEST,
                        "Invalid OTP code."),
        OTP_MAX_ATTEMPTS_EXCEEDED(
                        HttpStatus.BAD_REQUEST,
                        "Too many incorrect attempts. Please request a new OTP."),
        OTP_VERIFICATION_TOKEN_INVALID(
                        HttpStatus.BAD_REQUEST,
                        "OTP verification token is invalid or expired."),

        // --- Registration errors ---
        PHONE_ALREADY_EXISTS(
                        HttpStatus.CONFLICT,
                        "An account with this phone number already exists."),
        EMAIL_ALREADY_EXISTS(
                        HttpStatus.CONFLICT,
                        "An account with this email already exists."),
        USERNAME_ALREADY_EXISTS(
                        HttpStatus.CONFLICT,
                        "An account with this username already exists."),

        // --- Login errors ---
        INVALID_CREDENTIALS(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid phone number or password."),
        ACCOUNT_NOT_VERIFIED(
                        HttpStatus.FORBIDDEN,
                        "Account phone number is not verified yet."),
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
