package com.backend.community_service.service;

import com.backend.community_service.client.UserServiceClient;
import com.backend.community_service.constants.CacheNames;
import com.backend.community_service.dto.AuthorInfo;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorCacheService {

    private final UserServiceClient userServiceClient;

    @Cacheable(value = CacheNames.AUTHOR, key = "#userId")
    public AuthorInfo getAuthor(UUID userId) {
        try {
            return userServiceClient.getAuthor(userId);
        } catch (FeignException e) {
            log.warn("user-service unavailable for userId={}, using fallback", userId);
            return AuthorInfo.unknown(userId);
        }
    }

    public Map<UUID, AuthorInfo> getAuthors(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty())
            return Map.of();

        List<UUID> distinct = userIds.stream().distinct().toList();

        try {
            Map<UUID, AuthorInfo> result = userServiceClient.getAllAuthors(distinct);
            result.forEach((id, author) -> warmCache(id, author));
            return result;
        } catch (FeignException e) {
            log.warn("user-service unavailable for batch load, falling back to per-id cache");
            // Cache hit via @Cacheable proxy on getAuthor()
            return distinct.stream()
                    .collect(Collectors.toMap(id -> id, this::getAuthor));
        }
    }

    @CacheEvict(value = CacheNames.AUTHOR, key = "#userId")
    public void evict(UUID userId) {
        log.debug("Evicted author cache for userId={}", userId);
    }

    @CachePut(value = CacheNames.AUTHOR, key = "#userId")
    public AuthorInfo warmCache(UUID userId, AuthorInfo author) {
        return author;
    }
}