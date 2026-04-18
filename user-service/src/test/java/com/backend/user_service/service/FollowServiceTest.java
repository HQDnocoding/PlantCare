package com.backend.user_service.service;

import com.backend.user_service.dto.UserProfileResponse;
import com.backend.user_service.entity.UserProfile;
import com.backend.user_service.repository.FollowRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;

    @InjectMocks
    private FollowService followService;

    @Nested
    @DisplayName("getFollowers()")
    class GetFollowersTests {

        @Test
        @DisplayName("returns list of follower profiles")
        void getFollowers_returnsProfiles() {
            UUID userId = UUID.randomUUID();
            UUID follower1Id = UUID.randomUUID();
            UUID follower2Id = UUID.randomUUID();

            UserProfile follower1 = UserProfile.builder()
                    .userId(follower1Id)
                    .displayName("Follower 1")
                    .bio("Bio 1")
                    .avatarUrl("https://example.com/avatar1.jpg")
                    .isDeleted(false)
                    .createdAt(Instant.now())
                    .build();

            UserProfile follower2 = UserProfile.builder()
                    .userId(follower2Id)
                    .displayName("Follower 2")
                    .bio("Bio 2")
                    .avatarUrl("https://example.com/avatar2.jpg")
                    .isDeleted(false)
                    .createdAt(Instant.now())
                    .build();

            when(followRepository.findFollowerProfiles(userId)).thenReturn(List.of(follower1, follower2));

            List<UserProfileResponse> result = followService.getFollowers(userId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getDisplayName()).isEqualTo("Follower 1");
            assertThat(result.get(1).getDisplayName()).isEqualTo("Follower 2");
        }

        @Test
        @DisplayName("returns empty list when no followers")
        void getFollowers_noFollowers_returnsEmptyList() {
            UUID userId = UUID.randomUUID();
            when(followRepository.findFollowerProfiles(userId)).thenReturn(List.of());

            List<UserProfileResponse> result = followService.getFollowers(userId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getFollowing()")
    class GetFollowingTests {

        @Test
        @DisplayName("returns list of following profiles")
        void getFollowing_returnsProfiles() {
            UUID userId = UUID.randomUUID();
            UUID following1Id = UUID.randomUUID();
            UUID following2Id = UUID.randomUUID();

            UserProfile following1 = UserProfile.builder()
                    .userId(following1Id)
                    .displayName("Following 1")
                    .bio("Following bio 1")
                    .avatarUrl("https://example.com/following1.jpg")
                    .isDeleted(false)
                    .createdAt(Instant.now())
                    .build();

            UserProfile following2 = UserProfile.builder()
                    .userId(following2Id)
                    .displayName("Following 2")
                    .bio("Following bio 2")
                    .avatarUrl("https://example.com/following2.jpg")
                    .isDeleted(false)
                    .createdAt(Instant.now())
                    .build();

            when(followRepository.findFollowingProfiles(userId)).thenReturn(List.of(following1, following2));

            List<UserProfileResponse> result = followService.getFollowing(userId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getDisplayName()).isEqualTo("Following 1");
            assertThat(result.get(1).getDisplayName()).isEqualTo("Following 2");
        }

        @Test
        @DisplayName("returns empty list when not following anyone")
        void getFollowing_notFollowingAnyone_returnsEmptyList() {
            UUID userId = UUID.randomUUID();
            when(followRepository.findFollowingProfiles(userId)).thenReturn(List.of());

            List<UserProfileResponse> result = followService.getFollowing(userId);

            assertThat(result).isEmpty();
        }
    }
}
