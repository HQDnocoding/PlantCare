package com.backend.auth.service;

import com.backend.auth.domain.dto.response.AdminUserResponse;
import com.backend.auth.domain.entity.User;
import com.backend.auth.exception.AppException;
import com.backend.auth.exception.ErrorCode;
import com.backend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public Map<String, Object> adminLogin(String email, String password) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getRole() != User.Role.ADMIN) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() == User.UserStatus.BLOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_BLOCKED);
        }
        if (!user.hasPassword() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtService.generateAccessToken(user);
        log.info("[Admin] Login success for email={}", email);
        return Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId().toString(),
                        "email", user.getEmail() != null ? user.getEmail() : "",
                        "fullName", user.getFullName()));
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getAllUsers(int page, int size) {
        return userRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(AdminUserResponse::from);
    }

    @Transactional
    public AdminUserResponse updateStatus(UUID userId, String status) {
        User.UserStatus newStatus;
        try {
            newStatus = User.UserStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Invalid status value: " + status + ". Must be ACTIVE, BLOCKED, or UNVERIFIED.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        user.setStatus(newStatus);
        userRepository.save(user);
        log.info("[Admin] Updated status of user {} to {}", userId, newStatus);
        return AdminUserResponse.from(user);
    }

    @Transactional
    public AdminUserResponse updateRole(UUID userId, String role) {
        User.Role newRole;
        try {
            newRole = User.Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Invalid role value: " + role + ". Must be FARMER, AGRONOMIST, or ADMIN.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        user.setRole(newRole);
        userRepository.save(user);
        log.info("[Admin] Updated role of user {} to {}", userId, newRole);
        return AdminUserResponse.from(user);
    }
}
