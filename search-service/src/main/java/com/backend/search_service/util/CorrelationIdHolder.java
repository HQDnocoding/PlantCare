package com.backend.search_service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

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