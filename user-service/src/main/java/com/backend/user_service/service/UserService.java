package com.backend.user_service.service;

import com.backend.user_service.dto.UpdateProfileRequest;
import com.backend.user_service.dto.UserProfileResponse;
import com.backend.user_service.entity.Follow;
import com.backend.user_service.entity.UserProfile;
import com.backend.user_service.event.UserDeletedEvent;
import com.backend.user_service.event.UserFollowedEvent;
import com.backend.user_service.event.UserUpdatedEvent;
import com.backend.user_service.exception.AppException;
import com.backend.user_service.exception.ErrorCode;
import com.backend.user_service.repository.FollowRepository;
import com.backend.user_service.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserProfileRepository profileRepo;
    private final FollowRepository followRepo;
    private final FirebaseStorageService storageService;
    private final OutboxService outboxService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID targetUserId, UUID requesterId) {
        UserProfile profile = profileRepo.findActiveByUserId(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return buildResponse(profile, requesterId);
    }

    @Transactional
    public UserProfileResponse createProfile(UUID userId, String displayName, String avatarUrl) {
        if (profileRepo.existsByUserId(userId)) {
            log.warn("Profile already exists for user {}, skipping creation", userId);
            return getProfileInternal(userId);
        }

        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .displayName(displayName)
                .avatarUrl(avatarUrl)
                .build();

        profileRepo.save(profile);
        outboxService.save("user.updated", userId,
                UserUpdatedEvent.builder()
                        .userId(userId.toString())
                        .displayName(profile.getDisplayName())
                        .bio(profile.getBio())
                        .avatarUrl(profile.getAvatarUrl())
                        .build());
        return buildResponse(profile, null);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        UserProfile profile = getActiveProfile(userId);

        profile.setDisplayName(req.getDisplayName());
        profile.setBio(req.getBio());

        profileRepo.save(profile);
        outboxService.save("user.updated", profile.getUserId(),
                UserUpdatedEvent.builder()
                        .userId(profile.getUserId().toString())
                        .displayName(profile.getDisplayName())
                        .bio(profile.getBio())
                        .avatarUrl(profile.getAvatarUrl())
                        .build());
        return buildResponse(profile, userId);
    }

    // ─── Update avatar ──────────────────────────────────────────────────────

    @Transactional
    public UserProfileResponse updateAvatar(UUID userId, MultipartFile file) {
        UserProfile profile = getActiveProfile(userId);

        // Xóa avatar cũ nếu có
        if (profile.getAvatarPath() != null) {
            storageService.deleteFile(profile.getAvatarPath());
        }

        String[] result = storageService.uploadAvatar(userId, file);
        profile.setAvatarUrl(result[0]);
        profile.setAvatarPath(result[1]);

        profileRepo.save(profile);
        outboxService.save("user.updated", profile.getUserId(),
                UserUpdatedEvent.builder()
                        .userId(profile.getUserId().toString())
                        .displayName(profile.getDisplayName())
                        .bio(profile.getBio())
                        .avatarUrl(profile.getAvatarUrl())
                        .build());
        log.info("Updated avatar for user {}", userId);
        return buildResponse(profile, userId);
    }

    // ─── Soft delete ─────────────────────────────────────────────────────────

    @Transactional
    public void deleteAccount(UUID userId) {
        UserProfile profile = getActiveProfile(userId);
        profile.setIsDeleted(true);
        profile.setDeletedAt(Instant.now());
        profileRepo.save(profile);

        // Cùng transaction → atomic
        outboxService.save("user.deleted", userId,
                UserDeletedEvent.builder()
                        .userId(userId.toString())
                        .build());
    }

    // ─── Follow / Unfollow ───────────────────────────────────────────────────

    @Transactional
    public void follow(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw new AppException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        // Kiểm tra target user tồn tại
        if (!profileRepo.existsByUserId(followingId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (followRepo.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new AppException(ErrorCode.ALREADY_FOLLOWING);
        }

        followRepo.save(Follow.builder()
                .followerId(followerId)
                .followingId(followingId)
                .build());

        UserProfile followerProfile = profileRepo.findActiveByUserId(followerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        outboxService.save("user.followed", followerId,
                UserFollowedEvent.builder()
                        .followerId(followerId.toString())
                        .followerName(followerProfile.getDisplayName())
                        .followerAvatar(followerProfile.getAvatarUrl())
                        .followingId(followingId.toString())
                        .build());

        log.info("User {} followed {}", followerId, followingId);
    }

    @Transactional
    public void unfollow(UUID followerId, UUID followingId) {
        if (!followRepo.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new AppException(ErrorCode.NOT_FOLLOWING);
        }
        followRepo.deleteByFollowerIdAndFollowingId(followerId, followingId);
        log.info("User {} unfollowed {}", followerId, followingId);
    }

    // ─── Internal — Admin Service ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<UserProfile> getAllUsers(int page, int size) {
        return profileRepo.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @Transactional
    public void deleteUserByAdmin(UUID userId) {
        UserProfile profile = profileRepo.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        profile.setIsDeleted(true);
        profile.setDeletedAt(Instant.now());
        profileRepo.save(profile);

        outboxService.save("user.deleted", userId,
                UserDeletedEvent.builder()
                        .userId(userId.toString())
                        .build());
        log.info("[Admin] Soft-deleted user {}", userId);
    }

    // ─── Internal (gọi từ community-service qua Feign) ──────────────────────

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileInternal(UUID userId) {
        UserProfile profile = profileRepo.findActiveByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return buildResponse(profile, null);
    }

    @Transactional(readOnly = true)
    public List<UUID> getFollowingIds(UUID userId) {
        return followRepo.findFollowingIdsByFollowerId(userId);
    }

    /**
     * Batch load user profiles - tránh N+1 query problem khi load author list
     * 
     * @param userIds danh sách user IDs
     * @return Map<userId, profile>
     */
    @Transactional(readOnly = true)
    public Map<UUID, UserProfileResponse> getProfilesBatch(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty())
            return Map.of();

        List<UserProfile> profiles = profileRepo.findAllByUserIdIn(userIds);
        return profiles.stream()
                .collect(Collectors.toMap(
                        UserProfile::getUserId,
                        p -> buildResponse(p, p.getUserId())));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private UserProfile getActiveProfile(UUID userId) {
        return profileRepo.findActiveByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private UserProfileResponse buildResponse(UserProfile profile, UUID requesterId) {
        long followerCount = followRepo.countByFollowingId(profile.getUserId());
        long followingCount = followRepo.countByFollowerId(profile.getUserId());
        boolean isFollowedByMe = requesterId != null
                && !requesterId.equals(profile.getUserId())
                && followRepo.existsByFollowerIdAndFollowingId(requesterId, profile.getUserId());

        return UserProfileResponse.builder()
                .userId(profile.getUserId())
                .displayName(profile.getDisplayName())
                .bio(profile.getBio())
                .avatarUrl(profile.getAvatarUrl())
                .followerCount(followerCount)
                .followingCount(followingCount)
                .isFollowedByMe(isFollowedByMe)
                .createdAt(profile.getCreatedAt())
                .build();
    }
}
