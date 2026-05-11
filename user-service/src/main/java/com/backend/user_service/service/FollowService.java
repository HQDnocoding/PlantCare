package com.backend.user_service.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.user_service.dto.UserProfileResponse;
import com.backend.user_service.entity.UserProfile;
import com.backend.user_service.repository.FollowRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowService {
    private final FollowRepository followRepo;

    // ── Lấy danh sách Người theo dõi (Followers) ──────────────────────────────
    @Transactional(readOnly = true)
    public List<UserProfileResponse> getFollowers(UUID userId) {
        List<UserProfile> profiles = followRepo.findFollowerProfiles(userId);
        return profiles.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    // ── Lấy danh sách Đang theo dõi (Following) ──────────────────────────────
    @Transactional(readOnly = true)
    public List<UserProfileResponse> getFollowing(UUID userId) {
        List<UserProfile> profiles = followRepo.findFollowingProfiles(userId);
        return profiles.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    private UserProfileResponse convertToResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .userId(profile.getUserId())
                .displayName(profile.getDisplayName())
                .avatarUrl(profile.getAvatarUrl())
                .bio(profile.getBio())
                .build();
    }

}
