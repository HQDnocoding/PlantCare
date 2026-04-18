package com.backend.community_service.service;

import com.backend.community_service.entity.Comment;
import com.backend.community_service.entity.Post;
import com.backend.community_service.entity.Vote;
import com.backend.community_service.exception.AppException;
import com.backend.community_service.exception.ErrorCode;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VoteService")
class VoteServiceTest {

    @Mock
    private VoteRepository voteRepo;
    @Mock
    private PostRepository postRepo;
    @Mock
    private CommentRepository commentRepo;
    @Mock
    private OutboxService outboxService;
    @Mock
    private AuthorCacheService authorCache;

    @InjectMocks
    private VoteService voteService;

    @Nested
    @DisplayName("vote()")
    class VoteTests {

        @Test
        @DisplayName("should create new upvote on post")
        void vote_createNewUpvote() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID postId = UUID.randomUUID();
            Post post = createPost(postId);

            when(postRepo.findByIdAndIsDeletedFalseForUpdate(postId)).thenReturn(Optional.of(post));
            when(voteRepo.findByUserIdAndTargetIdAndTargetType(userId, postId, Vote.TargetType.POST))
                    .thenReturn(Optional.empty());
            when(voteRepo.save(any(Vote.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Short result = voteService.vote(userId, postId, Vote.TargetType.POST, (short) 1);

            // Assert
            assertThat(result).isEqualTo((short) 1);
            verify(voteRepo).save(any(Vote.class));
        }

        @Test
        @DisplayName("should toggle vote when same direction")
        void vote_toggleOffVote() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID postId = UUID.randomUUID();
            Post post = createPost(postId);

            Vote existingVote = Vote.builder()
                    .userId(userId)
                    .targetId(postId)
                    .targetType(Vote.TargetType.POST)
                    .value((short) 1)
                    .build();

            when(postRepo.findByIdAndIsDeletedFalseForUpdate(postId)).thenReturn(Optional.of(post));
            when(voteRepo.findByUserIdAndTargetIdAndTargetType(userId, postId, Vote.TargetType.POST))
                    .thenReturn(Optional.of(existingVote));

            // Act
            Short result = voteService.vote(userId, postId, Vote.TargetType.POST, (short) 1);

            // Assert
            assertThat(result).isNull();
            verify(voteRepo).delete(existingVote);
        }

        @Test
        @DisplayName("should throw exception for invalid vote value")
        void vote_invalidValue() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID postId = UUID.randomUUID();

            // Act & Assert
            assertThatThrownBy(() -> voteService.vote(userId, postId, Vote.TargetType.POST, (short) 2))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_VOTE_VALUE));
        }

        @Test
        @DisplayName("should throw exception when post not found")
        void vote_postNotFound() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID postId = UUID.randomUUID();

            when(postRepo.findByIdAndIsDeletedFalseForUpdate(postId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> voteService.vote(userId, postId, Vote.TargetType.POST, (short) 1))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.VOTE_TARGET_NOT_FOUND));
        }

        @Test
        @DisplayName("should create new downvote on comment")
        void vote_createDownvoteOnComment() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID commentId = UUID.randomUUID();
            Comment comment = Comment.builder()
                    .id(commentId)
                    .postId(UUID.randomUUID())
                    .content("Test")
                    .createdAt(Instant.now())
                    .build();

            when(commentRepo.findByIdAndIsDeletedFalseForUpdate(commentId)).thenReturn(Optional.of(comment));
            when(voteRepo.findByUserIdAndTargetIdAndTargetType(userId, commentId, Vote.TargetType.COMMENT))
                    .thenReturn(Optional.empty());
            when(voteRepo.save(any(Vote.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            Short result = voteService.vote(userId, commentId, Vote.TargetType.COMMENT, (short) -1);

            // Assert
            assertThat(result).isEqualTo((short) -1);
            verify(voteRepo).save(any(Vote.class));
        }
    }

    private Post createPost(UUID postId) {
        return Post.builder()
                .id(postId)
                .authorId(UUID.randomUUID())
                .content("Test post")
                .createdAt(Instant.now())
                .build();
    }
}
