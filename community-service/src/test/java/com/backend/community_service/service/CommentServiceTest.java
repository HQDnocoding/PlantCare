package com.backend.community_service.service;

import com.backend.community_service.dto.AuthorInfo;
import com.backend.community_service.dto.CommentResponse;
import com.backend.community_service.dto.CursorPage;
import com.backend.community_service.entity.Comment;
import com.backend.community_service.entity.Post;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService")
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepo;
    @Mock
    private PostRepository postRepo;
    @Mock
    private VoteRepository voteRepo;
    @Mock
    private AuthorCacheService authorCache;
    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private CommentService commentService;

    @Nested
    @DisplayName("getComments()")
    class GetCommentsTests {

        @Test
        @DisplayName("should return paginated comments with proper assertions")
        void getComments_success() {
            // Arrange
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();
            Instant cursor = Instant.now();

            Comment c1 = Comment.builder()
                    .id(UUID.randomUUID())
                    .postId(postId)
                    .authorId(authorId)
                    .content("Comment 1")
                    .createdAt(Instant.now())
                    .build();

            AuthorInfo author = AuthorInfo.builder().userId(authorId).displayName("Author").build();

            ReflectionTestUtils.setField(commentService, "defaultPageSize", 20);
            // Must mock postRepo method used by service
            Post mockPost = Post.builder()
                    .id(postId)
                    .authorId(authorId)
                    .content("Test post")
                    .createdAt(Instant.now())
                    .build();
            when(postRepo.findByIdAndIsDeletedFalse(postId)).thenReturn(Optional.of(mockPost));
            when(commentRepo.findTopLevelByPostId(postId, cursor, PageRequest.of(0, 21)))
                    .thenReturn(List.of(c1));
            when(authorCache.getAuthors(List.of(authorId))).thenReturn(Map.of(authorId, author));
            when(voteRepo.findByUserIdAndTargetTypeAndTargetIdIn(eq(requesterId),
                    eq(com.backend.community_service.entity.Vote.TargetType.COMMENT), anyList()))
                    .thenReturn(List.of());

            // Act
            CursorPage<CommentResponse> page = commentService.getComments(postId, cursor, 20, requesterId);

            // Assert
            assertThat(page).isNotNull();
            assertThat(page.getItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getReplies()")
    class GetRepliesTests {

        @Test
        @DisplayName("should retrieve replies for a comment")
        void getReplies_success() {
            // Arrange
            UUID parentId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            UUID requesterId = UUID.randomUUID();

            Comment reply = Comment.builder()
                    .id(UUID.randomUUID())
                    .parentId(parentId)
                    .authorId(authorId)
                    .content("Reply")
                    .createdAt(Instant.now())
                    .build();

            AuthorInfo author = AuthorInfo.builder().userId(authorId).displayName("Author").build();

            ReflectionTestUtils.setField(commentService, "defaultPageSize", 20);
            when(commentRepo.findRepliesByParentId(parentId, null, PageRequest.of(0, 21)))
                    .thenReturn(List.of(reply));
            when(authorCache.getAuthors(List.of(authorId))).thenReturn(Map.of(authorId, author));
            when(voteRepo.findByUserIdAndTargetTypeAndTargetIdIn(eq(requesterId),
                    eq(com.backend.community_service.entity.Vote.TargetType.COMMENT), anyList()))
                    .thenReturn(List.of());

            // Act
            CursorPage<CommentResponse> page = commentService.getReplies(parentId, null, 20, requesterId);

            // Assert
            assertThat(page).isNotNull();
            assertThat(page.getItems()).hasSize(1);
        }
    }

}
