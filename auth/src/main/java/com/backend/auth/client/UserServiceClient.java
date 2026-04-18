package com.backend.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.backend.auth.config.FeignInternalConfig;
import com.backend.auth.client.dto.CreateProfileRequest;

/**
 * Feign Client for interacting with the User Service.
 * Used primarily to synchronize user identity creation with profile data.
 * * The configuration 'FeignInternalConfig' ensures that all internal requests
 * include the necessary security headers (X-Internal-Secret, etc.).
 */
@FeignClient(name = "user-service", url = "${feign.client.config.user-service.url:http://localhost:8083}", configuration = FeignInternalConfig.class)
public interface UserServiceClient {

    /**
     * Triggers the creation of a new user profile in the User Service.
     * This is called immediately after a successful registration in the Auth
     * Service.
     * * @param request Data containing user ID and basic profile info
     */
    @PostMapping("/internal/v1/users")
    void createProfile(@RequestBody CreateProfileRequest request);
}