package com.backend.user_service.service;

import com.backend.user_service.dto.UpdateProfileRequest;
import com.backend.user_service.dto.UserProfileResponse;
import com.backend.user_service.entity.Follow;
import com.backend.user_service.entity.UserProfile;
import com.backend.user_service.exception.AppException;
import com.backend.user_service.exception.ErrorCode;
import com.backend.user_service.repository.FollowRepository;
import com.backend.user_service.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserProfileRepository profileRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private FirebaseStorageService storageService;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<UserProfile> profileCaptor;

    private UUID testUserId;
    private UUID testRequesterId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testRequesterId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("getProfile()")
    class GetProfileTests {

        @Test
        @DisplayName("returns user profile with follower/following counts")
        void getProfile_returnsResponse() {
            UserProfile profile = UserProfile.builder()
                    .userId(testUserId)
                    .displayName("Farmer John")
                    .bio("Growing vegetables")
                    .avatarUrl("https://example.com/avatar.jpg")
                    .isDeleted(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(profileRepository.findActiveByUserId(testUserId)).thenReturn(Optional.of(profile));
            when(followRepository.countByFollowingId(testUserId)).thenReturn(10L);
            when(followRepository.countByFollowerId(testUserId)).thenReturn(5L);
            when(followRepository.existsByFollowerIdAndFollowingId(testRequesterId, testUserId)).thenReturn(false);

            UserProfileResponse response = userService.getProfile(testUserId, testRequesterId);

            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getDisplayName()).isEqualTo("Farmer John");
            assertThat(response.getFollowerCount()).isEqualTo(10L);
            assertThat(response.getFollowingCount()).isEqualTo(5L);
            assertThat(response.isFollowedByMe()).isFalse();
        }

        @Test
        @DisplayName("throws USER_NOT_FOUND when profile not found")
        void getProfile_notFound_throwsException() {
            when(profileRepository.findActiveByUserId(testUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfile(testUserId, testRequesterId))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("createProfile()")
    class CreateProfileTests {

        @Test
        @DisplayName("creates new user profile and saves outbox event")
        void createProfile_savesAndPublishesEvent() {
            when(profileRepository.existsByUserId(testUserId)).thenReturn(false);
            when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserProfileResponse response = userService.createProfile(testUserId, "New Farmer",
                    "https://example.com/avatar.jpg");

            assertThat(response.getUserId()).isEqualTo(testUserId);
            assertThat(response.getDisplayName()).isEqualTo("New Farmer");

            verify(profileRepository, times(1)).save(profileCaptor.capture());
            UserProfile saved = profileCaptor.getValue();
            assertThat(saved.getUserId()).isEqualTo(testUserId);
            assertThat(saved.getDisplayName()).isEqualTo("New Farmer");
            assertThat(saved.getAvatarUrl()).isEqualTo("https://example.com/avatar.jpg");

            verify(outboxService, times(1)).save(eq("user.updated"), eq(testUserId), any());
        }

        @Test
        @DisplayName("skips creation if profile already exists")
        void createProfile_alreadyExists_skips() {
            when(profileRepository.existsByUserId(testUserId)).thenReturn(true);
            UserProfile existingProfile = UserProfile.builder()
                    .userId(testUserId)
                    .displayName("Existing User")
                    .isDeleted(false)
                    .build();
            when(profileRepository.findActiveByUserId(testUserId)).thenReturn(Optional.of(existingProfile));
            when(followRepository.countByFollowingId(testUserId)).thenReturn(0L);
            when(followRepository.countByFollowerId(testUserId)).thenReturn(0L);

            userService.createProfile(testUserId, "Ignored", null);

            verify(profileRepository, never()).save(any(UserProfile.class));
        }
    }

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfileTests {

        @Test
        @DisplayName("updates display name and bio")
        void updateProfile_success() {
            UserProfile profile = UserProfile.builder()
                    .userId(testUserId)
                    .displayName("Old Name")
                    .bio("Old bio")
                    .isDeleted(false)
                    .build();

            when(profileRepository.findActiveByUserId(testUserId)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(followRepository.countByFollowingId(testUserId)).thenReturn(0L);
            when(followRepository.countByFollowerId(testUserId)).thenReturn(0L);

            UpdateProfileRequest request = new UpdateProfileRequest();
            request.setDisplayName("New Name");
            request.setBio("New bio");

            UserProfileResponse response = userService.updateProfile(testUserId, request);

            assertThat(response.getDisplayName()).isEqualTo("New Name");
            verify(profileRepository, times(1)).save(profileCaptor.capture());
            assertThat(profileCaptor.getValue().getBio()).isEqualTo("New bio");
            verify(outboxService, times(1)).save(eq("user.updated"), eq(testUserId), any());
        }
    }

    @Nested
    @DisplayName("deleteAccount()")
    class DeleteAccountTests {

        @Test
        @DisplayName("soft deletes user profile and publishes event")
        void deleteAccount_softDeletes() {
            UserProfile profile = UserProfile.builder()
                    .userId(testUserId)
                    .displayName("User to Delete")
                    .isDeleted(false)
                    .build();

            when(profileRepository.findActiveByUserId(testUserId)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.deleteAccount(testUserId);

            verify(profileRepository, times(1)).save(profileCaptor.capture());
            UserProfile deleted = profileCaptor.getValue();
            assertThat(deleted.getIsDeleted()).isTrue();
            assertThat(deleted.getDeletedAt()).isNotNull();
            verify(outboxService, times(1)).save(eq("user.deleted"), eq(testUserId), any());
        }
    }

    @Nested
    @DisplayName("Follow/Unfollow")
    class FollowTests {

        @Test
        @DisplayName("follow succeeds when both users exist and not already following")
        void follow_success() {
            UUID followingId = UUID.randomUUID();
            UserProfile followerProfile = UserProfile.builder()
                    .userId(testUserId)
                    .displayName("Follower")
                    .isDeleted(false)
                    .build();

            when(profileRepository.existsByUserId(followingId)).thenReturn(true);
            when(followRepository.existsByFollowerIdAndFollowingId(testUserId, followingId)).thenReturn(false);
            when(profileRepository.findActiveByUserId(testUserId)).thenReturn(Optional.of(followerProfile));

            userService.follow(testUserId, followingId);

            verify(followRepository, times(1)).save(any(Follow.class));
            verify(outboxService, times(1)).save(eq("user.followed"), eq(testUserId), any());
        }

        @Test
        @DisplayName("cannot follow self")
        void follow_self_throwsException() {
            assertThatThrownBy(() -> userService.follow(testUserId, testUserId))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CANNOT_FOLLOW_SELF);
        }

        @Test
        @DisplayName("throws ALREADY_FOLLOWING when already following")
        void follow_alreadyFollowing_throwsException() {
            UUID followingId = UUID.randomUUID();
            when(profileRepository.existsByUserId(followingId)).thenReturn(true);
            when(followRepository.existsByFollowerIdAndFollowingId(testUserId, followingId)).thenReturn(true);

            assertThatThrownBy(() -> userService.follow(testUserId, followingId))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ALREADY_FOLLOWING);
        }

        @Test
        @DisplayName("unfollow removes follow relationship")
        void unfollow_success() {
            UUID followingId = UUID.randomUUID();
            when(followRepository.existsByFollowerIdAndFollowingId(testUserId, followingId)).thenReturn(true);

            userService.unfollow(testUserId, followingId);

            verify(followRepository, times(1)).deleteByFollowerIdAndFollowingId(testUserId, followingId);
        }

        @Test
        @DisplayName("unfollow throws NOT_FOLLOWING when not following")
        void unfollow_notFollowing_throwsException() {
            UUID followingId = UUID.randomUUID();
            when(followRepository.existsByFollowerIdAndFollowingId(testUserId, followingId)).thenReturn(false);

            assertThatThrownBy(() -> userService.unfollow(testUserId, followingId))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_FOLLOWING);
        }
    }

    @Nested
    @DisplayName("getProfilesBatch()")
    class GetProfilesBatchTests {

        @Test
        @DisplayName("batch loads user profiles")
        void getProfilesBatch_returnsMap() {
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();
            List<UUID> userIds = List.of(userId1, userId2);

            UserProfile profile1 = UserProfile.builder()
                    .userId(userId1)
                    .displayName("User 1")
                    .isDeleted(false)
                    .build();
            UserProfile profile2 = UserProfile.builder()
                    .userId(userId2)
                    .displayName("User 2")
                    .isDeleted(false)
                    .build();

            when(profileRepository.findAllByUserIdIn(userIds)).thenReturn(List.of(profile1, profile2));
            when(followRepository.countByFollowingId(userId1)).thenReturn(0L);
            when(followRepository.countByFollowerId(userId1)).thenReturn(0L);
            when(followRepository.countByFollowingId(userId2)).thenReturn(0L);
            when(followRepository.countByFollowerId(userId2)).thenReturn(0L);

            Map<UUID, UserProfileResponse> result = userService.getProfilesBatch(userIds);

            assertThat(result).hasSize(2);
            assertThat(result).containsKeys(userId1, userId2);
        }

        @Test
        @DisplayName("returns empty map for empty input")
        void getProfilesBatch_emptyList_returnsEmptyMap() {
            Map<UUID, UserProfileResponse> result = userService.getProfilesBatch(List.of());

            assertThat(result).isEmpty();
            verify(profileRepository, never()).findAllByUserIdIn(any());
        }
    }
}
