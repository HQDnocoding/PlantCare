package com.backend.user_service.service;

import com.backend.user_service.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCacheService {

    private final UserService userService;

    @Cacheable(value = "profile", key = "#userId")
    public UserProfileResponse getProfileWithCache(UUID userId) {
        log.debug("Cache miss for user profile: userId={}", userId);
        return userService.getProfile(userId, userId);
    }

    @CacheEvict(value = { "profile", "stats" }, key = "#userId")
    public void invalidateUserCache(UUID userId) {
        log.debug("Invalidated cache for userId={}", userId);
    }

    @CacheEvict(value = { "profile", "stats" }, allEntries = true)
    public void invalidateAllUserCache() {
        log.info("Invalidated all user cache");
    }
}
