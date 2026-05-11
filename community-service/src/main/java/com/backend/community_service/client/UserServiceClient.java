package com.backend.community_service.client;

import com.backend.community_service.config.FeignInternalConfig;
import com.backend.community_service.dto.AuthorInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "user-service", fallback = UserServiceClientFallback.class, url = "${USER_SERVICE_URL:http://localhost:8083}", configuration = FeignInternalConfig.class)
public interface UserServiceClient {

    @GetMapping("/internal/v1/users/{userId}")
    AuthorInfo getAuthor(@PathVariable UUID userId);

    @GetMapping("/internal/v1/users/{userId}/following-ids")
    List<UUID> getFollowingIds(@PathVariable("userId") UUID userId);

    /**
     * Batch load user profiles - avoid N+1 queries
     */
    @GetMapping("/internal/v1/users/batch")
    Map<UUID, AuthorInfo> getAllAuthors(@RequestParam("ids") List<UUID> ids);
}
