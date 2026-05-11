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

        return AuthorInfo.unknown(userId);
    }

    @Override
    public List<UUID> getFollowingIds(UUID userId) {
        return java.util.Collections.emptyList();
    }

    @Override
    public Map<UUID, AuthorInfo> getAllAuthors(List<UUID> ids) {

        return java.util.Collections.emptyMap();
    }
}