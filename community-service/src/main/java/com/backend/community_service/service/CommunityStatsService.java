package com.backend.community_service.service;

import com.backend.community_service.dto.CommunityStatsResponse;
import com.backend.community_service.repository.PostRepository;
import com.backend.community_service.repository.CommentRepository;
import com.backend.community_service.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityStatsService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final VoteRepository voteRepository;

    public CommunityStatsResponse getCommunityStats() {
        try {
            long totalPosts = postRepository.count();
            long totalComments = commentRepository.count();
            long totalVotes = voteRepository.count();

            // New posts in this week
            long newPostsThisWeek = getNewPostsThisWeek();

            // New comments in this week
            long newCommentsThisWeek = getNewCommentsThisWeek();

            return CommunityStatsResponse.builder()
                    .totalPosts(totalPosts)
                    .totalComments(totalComments)
                    .totalVotes(totalVotes)
                    .newPostsThisWeek(newPostsThisWeek)
                    .newCommentsThisWeek(newCommentsThisWeek)
                    .activeCommunities(0) // TODO: Implement if needed
                    .timestamp(System.currentTimeMillis())
                    .build();
        } catch (Exception ex) {
            log.error("Error fetching community stats", ex);
            return CommunityStatsResponse.builder()
                    .timestamp(System.currentTimeMillis())
                    .build();
        }
    }

    private long getNewPostsThisWeek() {
        // TODO: Implement logic để lấy posts mới trong tuần
        // Query database với createdAt >= now - 7 days
        // Tạm thời return 0
        return 0;
    }

    private long getNewCommentsThisWeek() {
        // TODO: Implement logic để lấy comments mới trong tuần
        // Query database với createdAt >= now - 7 days
        // Tạm thời return 0
        return 0;
    }
}
