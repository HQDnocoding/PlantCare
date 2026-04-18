package com.backend.community_service.client;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.backend.community_service.dto.AuthorInfo;

@Component
class UserServiceClientFallback implements UserServiceClient {

    @Override
    public AuthorInfo getAuthor(UUID userId) {
        // Trả về dữ liệu "Unknown" để giao diện không bị trống trơn khi user-service
        // sập
        return AuthorInfo.unknown(userId);
    }

    @Override
    public List<UUID> getFollowingIds(UUID userId) {
        // Trả về danh sách trống thay vì null để tránh NullPointerException
        return java.util.Collections.emptyList();
    }

    @Override
    public Map<UUID, AuthorInfo> getAllAuthors(List<UUID> ids) {
        // Batch fallback: trả về Map trống khi user-service down
        // AuthorCacheService sẽ handle fallback logic
        return java.util.Collections.emptyMap();
    }
}