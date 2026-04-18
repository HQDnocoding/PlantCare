package com.backend.user_service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * ThreadLocal holder for correlation ID
 * Allows Feign interceptors to access the correlation ID from the current
 * request
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CorrelationIdHolder {

    private static final ThreadLocal<String> holder = new ThreadLocal<>();

    public static void set(String correlationId) {
        holder.set(correlationId);
    }

    public static String get() {
        return holder.get();
    }

    public static void clear() {
        holder.remove();
    }
}
