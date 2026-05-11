package com.backend.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.backend.auth.config.FeignInternalConfig;
import com.backend.auth.client.dto.CreateProfileRequest;

@FeignClient(name = "user-service", url = "${feign.client.config.user-service.url:http://localhost:8083}", configuration = FeignInternalConfig.class)
public interface UserServiceClient {

    @PostMapping("/internal/v1/users")
    void createProfile(@RequestBody CreateProfileRequest request);
}