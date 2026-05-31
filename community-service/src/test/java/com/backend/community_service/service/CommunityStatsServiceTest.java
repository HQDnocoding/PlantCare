package com.backend.community_service.service;

import com.backend.community_service.dto.CommunityStatsResponse;
import com.backend.community_service.repository.CommentRepository;
import com.backend.community_service.repository.PostRepository;
import com.backend.community_service.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityStatsService")
class CommunityStatsServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private VoteRepository voteRepository;

    @InjectMocks
    private CommunityStatsService communityStatsService;

    @Nested
    @DisplayName("getCommunityStats()")
    class GetCommunityStatsTests {

        @Test
        @DisplayName("should return aggregated statistics")
        void getCommunityStats_success() {
            // Arrange
            when(postRepository.count()).thenReturn(150L);
            when(commentRepository.count()).thenReturn(450L);
            when(voteRepository.count()).thenReturn(2000L);

            // Act
            CommunityStatsResponse stats = communityStatsService.getCommunityStats();

            // Assert
            assertThat(stats).isNotNull();
            assertThat(stats.getTotalPosts()).isEqualTo(150);
            assertThat(stats.getTotalComments()).isEqualTo(450);
            assertThat(stats.getTotalVotes()).isEqualTo(2000);
            assertThat(stats.getTimestamp()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should return zero statistics for empty community")
        void getCommunityStats_empty() {
            // Arrange
            when(postRepository.count()).thenReturn(0L);
            when(commentRepository.count()).thenReturn(0L);
            when(voteRepository.count()).thenReturn(0L);

            // Act
            CommunityStatsResponse stats = communityStatsService.getCommunityStats();

            // Assert
            assertThat(stats).isNotNull();
            assertThat(stats.getTotalPosts()).isEqualTo(0);
            assertThat(stats.getTotalComments()).isEqualTo(0);
            assertThat(stats.getTotalVotes()).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle exceptions gracefully")
        void getCommunityStats_exceptionHandling() {
            // Arrange
            when(postRepository.count()).thenThrow(new RuntimeException("DB Error"));

            // Act
            CommunityStatsResponse stats = communityStatsService.getCommunityStats();

            // Assert
            assertThat(stats).isNotNull();
            assertThat(stats.getTimestamp()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should handle large values")
        void getCommunityStats_largeValues() {
            // Arrange
            when(postRepository.count()).thenReturn(1_000_000L);
            when(commentRepository.count()).thenReturn(5_000_000L);
            when(voteRepository.count()).thenReturn(50_000_000L);

            // Act
            CommunityStatsResponse stats = communityStatsService.getCommunityStats();

            // Assert
            assertThat(stats.getTotalPosts()).isEqualTo(1_000_000L);
            assertThat(stats.getTotalComments()).isEqualTo(5_000_000L);
            assertThat(stats.getTotalVotes()).isEqualTo(50_000_000L);
        }
    }
}
