package com.backend.community_service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// Static utility — instantiation is meaningless, block it
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CorrelationIdHolder {

    // Each thread gets its own isolated copy — safe for concurrent requests.
    // IMPORTANT: caller must invoke clear() in a finally block after the request
    // completes, or the value will leak into the next request on the same thread.
    private static final ThreadLocal<String> holder = new ThreadLocal<>();

    public static void set(String correlationId) {
        holder.set(correlationId);
    }

    public static String get() {
        return holder.get();
    }

    // Use remove() not set(null) — fully evicts the entry and prevents memory leaks
    // in thread-pool environments where threads are reused across requests.
    public static void clear() {
        holder.remove();
    }
}