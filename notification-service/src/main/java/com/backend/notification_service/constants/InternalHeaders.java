package com.backend.notification_service.constants;

public final class InternalHeaders {
    private InternalHeaders() {
    }

    public static final String INTERNAL_SECRET = "X-Internal-Secret";
    public static final String SERVICE_JWT = "X-Service-JWT";
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
}