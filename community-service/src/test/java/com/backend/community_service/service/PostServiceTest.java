package com.backend.community_service.service;

import com.backend.community_service.dto.AuthorInfo;
import com.backend.community_service.dto.PostCreateRequest;
import com.backend.community_service.dto.PostResponse;
import com.backend.community_service.entity.Post;
import com.backend.community_service.exception.AppException;
import com.backend.community_service.exception.ErrorCode;
import com.backend.community_service.repository.PostRepository;
import com.backend.community_service.repository.VoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostService")
class PostServiceTest {

    @Mock
    private PostRepository postRepo;
    @Mock
    private VoteRepository voteRepo;
    @Mock
    private AuthorCacheService authorCache;
    @Mock
    private FirebaseStorageService storageService;
    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private PostService postService;

    @Nested
    @DisplayName("deleteAllPostsByUser()")
    class DeleteAllPostsByUserTests {

        @Test
        @DisplayName("should soft-delete all user posts and delete Firebase images")
        void deleteAllPostsByUser_success() {
            // Arrange
            UUID userId = UUID.randomUUID();
            Post post = Post.builder()
                    .id(UUID.randomUUID())
                    .authorId(userId)
                    .content("Test post")
                    .imagePaths(new String[] { "path/to/image.jpg" })
                    .createdAt(Instant.now())
                    .build();

            when(postRepo.findAllActiveByAuthorId(userId)).thenReturn(List.of(post));
            doNothing().when(storageService).deleteFile(any());

            // Act
            postService.deleteAllPostsByUser(userId);

            // Assert
            ArgumentCaptor<List<Post>> captor = ArgumentCaptor.forClass(List.class);
            verify(postRepo).saveAll(captor.capture());
            List<Post> savedPosts = captor.getValue();
            assertThat(savedPosts).hasSize(1);
            assertThat(savedPosts.get(0).isDeleted()).isTrue();
        }

        @Test
        @DisplayName("should be idempotent when no posts exist")
        void deleteAllPostsByUser_noPosts() {
            // Arrange
            UUID userId = UUID.randomUUID();
            when(postRepo.findAllActiveByAuthorId(userId)).thenReturn(new ArrayList<>());

            // Act
            postService.deleteAllPostsByUser(userId);

            // Assert
            verify(postRepo, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("getAdminPostById()")
    class GetAdminPostByIdTests {

        @Test
        @DisplayName("should return admin post details")
        void getAdminPostById_success() {
            // Arrange
            UUID postId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            Post post = Post.builder()
                    .id(postId)
                    .authorId(authorId)
                    .content("Admin post")
                    .imageUrls(new String[] { "url1" })
                    .upvoteCount(5)
                    .downvoteCount(1)
                    .isDeleted(true)
                    .createdAt(Instant.now())
                    .build();

            when(postRepo.findById(postId)).thenReturn(Optional.of(post));

            // Act
            Map<String, Object> result = postService.getAdminPostById(postId);

            // Assert
            assertThat(result).containsEntry("id", postId.toString())
                    .containsEntry("content", "Admin post")
                    .containsEntry("isDeleted", true);
        }

        @Test
        @DisplayName("should throw POST_NOT_FOUND when post not found")
        void getAdminPostById_notFound() {
            // Arrange
            UUID postId = UUID.randomUUID();
            when(postRepo.findById(postId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> postService.getAdminPostById(postId))
                    .isInstanceOf(AppException.class)
                    .satisfies(
                            ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.POST_NOT_FOUND));
        }
    }
}
