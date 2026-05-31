package com.backend.auth.service;

import com.backend.auth.domain.dto.response.InternalUserResponse;
import com.backend.auth.domain.entity.User;
import com.backend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for internal cross-service communication.
 * Provides simplified user data to other microservices within the system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalUserService {

    private final UserRepository userRepository;

    /**
     * Retrieves internal user details by their unique ID.
     * Used by other services to verify user existence and permissions.
     *
     * @param userId The unique identifier of the user
     * @return Optional containing InternalUserResponse if found
     */
    public Optional<InternalUserResponse> findById(UUID userId) {
        return userRepository.findById(userId)
                .map(this::toResponse);
    }

    /**
     * Maps the User entity to a secure, flat response DTO.
     * Ensures internal-only fields like password hashes are never exposed.
     */
    private InternalUserResponse toResponse(User user) {
        // A user is considered active only if they are not soft-deleted
        // AND their status is explicitly ACTIVE.
        boolean isActive = user.getDeletedAt() == null &&
                user.getStatus() == User.UserStatus.ACTIVE;

        return new InternalUserResponse(
                user.getId(),
                user.getPhone(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                isActive);
    }
}