package com.backend.notification_service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// Static utility — instantiation is meaningless, block it
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