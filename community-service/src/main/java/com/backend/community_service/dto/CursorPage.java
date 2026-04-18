package com.backend.community_service.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Wrapper cho cursor-based pagination.
 *
 * Client dùng nextCursor để load trang tiếp theo:
 * GET /posts?cursor={nextCursor}&size=20
 *
 * nextCursor = null nghĩa là đã hết data.
 */
@Getter
@Builder
public class CursorPage<T> {
    private final List<T> items;
    private final String nextCursor; // Base64-encoded cursor string
    private final Boolean hasMore;
    private final int size;

    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return CursorPage.<T>builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasMore(nextCursor != null)
                .size(items.size())
                .build();
    }

    public static <T> CursorPage<T> empty() {
        return CursorPage.<T>builder()
                .items(List.of())
                .nextCursor(null)
                .hasMore(false)
                .size(0)
                .build();
    }
}
