package com.backend.user_service.service;

import com.backend.user_service.dto.UserStatsResponse;
import com.backend.user_service.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserStatsService userStatsService;

    @Nested
    @DisplayName("getUserStats()")
    class GetUserStatsTests {

        @Test
        @DisplayName("returns user stats with total user count")
        void getUserStats_returnsTotalUsers() {
            when(userProfileRepository.count()).thenReturn(150L);

            UserStatsResponse stats = userStatsService.getUserStats();

            assertThat(stats.getTotalUsers()).isEqualTo(150L);
            assertThat(stats.getTimestamp()).isGreaterThan(0);
        }

        @Test
        @DisplayName("returns stats with zero counts initialized")
        void getUserStats_returnsZeroForUnimplementedStats() {
            when(userProfileRepository.count()).thenReturn(50L);

            UserStatsResponse stats = userStatsService.getUserStats();

            assertThat(stats.getTotalUsers()).isEqualTo(50L);
            assertThat(stats.getActiveUsersToday()).isEqualTo(0L);
            assertThat(stats.getNewUsersThisWeek()).isEqualTo(0L);
            assertThat(stats.getMaleCount()).isEqualTo(0L);
            assertThat(stats.getFemaleCount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("handles exception and returns partial stats")
        void getUserStats_exception_returnsPartialStats() {
            when(userProfileRepository.count()).thenThrow(new RuntimeException("Database error"));

            UserStatsResponse stats = userStatsService.getUserStats();

            assertThat(stats.getTimestamp()).isGreaterThan(0);
            assertThat(stats.getTotalUsers()).isEqualTo(0L);
        }
    }
}
