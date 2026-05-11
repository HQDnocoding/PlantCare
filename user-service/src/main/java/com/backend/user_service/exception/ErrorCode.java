package com.backend.user_service.exception;

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
    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "Invalid request."),
    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized."),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "Forbidden."),

    // --- User ---
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "User not found."),
    USER_PROFILE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "User profile already exists."),
    USER_ALREADY_DELETED(
            HttpStatus.GONE,
            "User account is already deleted."),

    // --- Follow ---
    CANNOT_FOLLOW_SELF(
            HttpStatus.BAD_REQUEST,
            "Cannot follow yourself."),
    ALREADY_FOLLOWING(
            HttpStatus.CONFLICT,
            "Already following this user."),
    NOT_FOLLOWING(
            HttpStatus.BAD_REQUEST,
            "Not following this user."),

    // --- Upload ---
    INVALID_IMAGE_TYPE(
            HttpStatus.BAD_REQUEST,
            "Only JPEG and PNG images are allowed."),
    IMAGE_TOO_LARGE(
            HttpStatus.BAD_REQUEST,
            "Image size must not exceed 5MB."),
    UPLOAD_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to upload image.");

    private final HttpStatus httpStatus;
    private final String message;
}