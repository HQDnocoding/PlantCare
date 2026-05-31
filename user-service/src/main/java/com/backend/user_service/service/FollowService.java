package com.backend.user_service.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.user_service.dto.UserProfileResponse;
import com.backend.user_service.entity.Follow;
import com.backend.user_service.entity.UserProfile;
import com.backend.user_service.event.UserFollowedEvent;
import com.backend.user_service.repository.FollowRepository;
import com.backend.user_service.repository.UserProfileRepository;
import com.backend.user_service.service.OutboxService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowService {
    private final FollowRepository followRepo;
    private final UserProfileRepository userProfileRepo;
    private final OutboxService outboxService;

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

    // ── Follow a user ─────────────────────────────────────────────────────
    @Transactional
    public void follow(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw new IllegalArgumentException("Cannot follow yourself");
        }

        // Check if both users exist
        UserProfile followerProfile = userProfileRepo.findById(followerId)
                .orElseThrow(() -> new IllegalArgumentException("Follower not found"));
        userProfileRepo.findById(followingId)
                .orElseThrow(() -> new IllegalArgumentException("User to follow not found"));

        // Check if already following
        if (followRepo.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            log.debug("Already following | followerId={} followingId={}", followerId, followingId);
            return;
        }

        // Create follow relationship
        Follow follow = Follow.builder()
                .followerId(followerId)
                .followingId(followingId)
                .build();
        followRepo.save(follow);

        // Publish user.followed event
        outboxService.save("user.followed", followerId,
                UserFollowedEvent.builder()
                        .followerId(followerId.toString())
                        .followerName(followerProfile.getDisplayName())
                        .followerAvatar(followerProfile.getAvatarUrl())
                        .followingId(followingId.toString())
                        .build());

        log.info("Follow created | followerId={} followingId={}", followerId, followingId);
    }

    // ── Unfollow a user ───────────────────────────────────────────────────
    @Transactional
    public void unfollow(UUID followerId, UUID followingId) {
        followRepo.deleteByFollowerIdAndFollowingId(followerId, followingId);
        log.info("Unfollow executed | followerId={} followingId={}", followerId, followingId);
    }
}