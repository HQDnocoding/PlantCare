package com.backend.api_gateway.dto;

public record FallbackResponse(
        boolean success,
        String errorCode,
        String message,
        String timestamp) {
}