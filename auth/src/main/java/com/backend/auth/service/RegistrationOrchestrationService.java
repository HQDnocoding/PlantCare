package com.backend.auth.service;

import com.backend.auth.client.dto.CreateProfileRequest;
import com.backend.auth.domain.entity.User;
import com.backend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationOrchestrationService {

    private final UserRepository userRepository;
    private final OutboxService outboxService;

    @Transactional
    public User orchestrate(User draftUser) {
        User savedUser = userRepository.save(draftUser);
        enqueueProfileCreation(savedUser);
        return savedUser;
    }

    @Transactional
    public void enqueueProfileCreation(User user) {
        outboxService.save("user.created", user.getId(), buildCreateProfileCommand(user));
        log.info("Registration command queued via outbox | userId={}", user.getId());
    }

    private CreateProfileRequest buildCreateProfileCommand(User user) {
        return CreateProfileRequest.builder()
                .userId(user.getId())
                .displayName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
