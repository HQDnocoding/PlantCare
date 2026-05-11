package com.backend.community_service.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Generic
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have permission to perform this action."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Database error."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR"),
    // Post
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "Post not found."),
    POST_ALREADY_DELETED(HttpStatus.GONE, "Post has already been deleted."),
    POST_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "Post content must not be blank."),
    TOO_MANY_IMAGES(HttpStatus.BAD_REQUEST, "Maximum 4 images per post."),
    TOO_MANY_TAGS(HttpStatus.BAD_REQUEST, "Maximum 5 tags per post."),
    TAG_TOO_LONG(HttpStatus.BAD_REQUEST, "Tag must not exceed 32 characters."),

    // Comment
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Comment not found."),
    COMMENT_ALREADY_DELETED(HttpStatus.GONE, "Comment has already been deleted."),
    COMMENT_NOT_BELONG_TO_POST(HttpStatus.BAD_REQUEST, "Comment does not belong to this post."),
    REPLY_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "Cannot reply to a reply."),

    // Vote
    INVALID_VOTE_VALUE(HttpStatus.BAD_REQUEST, "Vote value must be 1 (upvote) or -1 (downvote)."),
    VOTE_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "Vote target not found."),

    // Upload
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, and WebP images are allowed."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "Each image must not exceed 5MB."),
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload image. Please try again."),

    // External service
    USER_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "User service is temporarily unavailable."),
    OUTBOX_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialize outbox event for topic: %s");

    private final HttpStatus httpStatus;
    private final String message;
}
