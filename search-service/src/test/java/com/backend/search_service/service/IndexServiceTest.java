package com.backend.search_service.service;

import com.backend.search_service.document.PostDocument;
import com.backend.search_service.document.UserDocument;
import com.backend.search_service.event.PostCreatedEvent;
import com.backend.search_service.event.PostUpdatedEvent;
import com.backend.search_service.event.UserUpdatedEvent;
import com.backend.search_service.repository.PostSearchRepository;
import com.backend.search_service.repository.UserSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexServiceTest {

    @Mock
    private PostSearchRepository postSearchRepository;

    @Mock
    private UserSearchRepository userSearchRepository;

    @InjectMocks
    private IndexService indexService;

    @Captor
    private ArgumentCaptor<PostDocument> postDocCaptor;

    @Captor
    private ArgumentCaptor<UserDocument> userDocCaptor;

    @Nested
    @DisplayName("indexPost()")
    class IndexPostTests {

        @Test
        @DisplayName("index new post saves document")
        void indexPost_save_successful() {
            PostCreatedEvent event = PostCreatedEvent.builder()
                    .postId("post-123")
                    .authorId("user-456")
                    .authorName("John Farmer")
                    .content("How to grow tomatoes")
                    .tags(Set.of("tomato", "gardening"))
                    .score(5)
                    .createdAt(Instant.now())
                    .build();

            when(postSearchRepository.save(any(PostDocument.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            indexService.indexPost(event);

            verify(postSearchRepository, times(1)).save(postDocCaptor.capture());
            PostDocument saved = postDocCaptor.getValue();
            assertThat(saved.getId()).isEqualTo("post-123");
            assertThat(saved.getAuthorId()).isEqualTo("user-456");
            assertThat(saved.getAuthorName()).isEqualTo("John Farmer");
            assertThat(saved.getContent()).isEqualTo("How to grow tomatoes");
            assertThat(saved.getTags()).containsExactlyInAnyOrder("tomato", "gardening");
            assertThat(saved.getScore()).isEqualTo(5);
        }

        @Test
        @DisplayName("indexPost handles exception silently")
        void indexPost_exception_silentFail() {
            PostCreatedEvent event = PostCreatedEvent.builder()
                    .postId("post-fail")
                    .authorId("user-fail")
                    .build();

            when(postSearchRepository.save(any(PostDocument.class)))
                    .thenThrow(new RuntimeException("Elasticsearch error"));

            indexService.indexPost(event);

            verify(postSearchRepository, times(1)).save(any(PostDocument.class));
        }
    }

    @Nested
    @DisplayName("updatePost()")
    class UpdatePostTests {

        @Test
        @DisplayName("update existing post updates content and tags")
        void updatePost_existing_updates() {
            PostDocument existingDoc = PostDocument.builder()
                    .id("post-123")
                    .authorId("user-456")
                    .authorName("John")
                    .content("Old content")
                    .tags(Set.of("old"))
                    .score(3)
                    .createdAt(Instant.now())
                    .build();

            PostUpdatedEvent event = PostUpdatedEvent.builder()
                    .postId("post-123")
                    .authorId("user-456")
                    .content("Updated content")
                    .tags(Set.of("updated", "new"))
                    .build();

            when(postSearchRepository.findById("post-123")).thenReturn(Optional.of(existingDoc));
            when(postSearchRepository.save(any(PostDocument.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            indexService.updatePost(event);

            verify(postSearchRepository, times(1)).save(postDocCaptor.capture());
            PostDocument updated = postDocCaptor.getValue();
            assertThat(updated.getContent()).isEqualTo("Updated content");
            assertThat(updated.getTags()).containsExactlyInAnyOrder("updated", "new");
        }

        @Test
        @DisplayName("update non-existing post creates new document (upsert)")
        void updatePost_notFound_createsNew() {
            PostUpdatedEvent event = PostUpdatedEvent.builder()
                    .postId("post-new")
                    .authorId("user-new")
                    .content("New content")
                    .tags(Set.of("new"))
                    .build();

            when(postSearchRepository.findById("post-new")).thenReturn(Optional.empty());
            when(postSearchRepository.save(any(PostDocument.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            indexService.updatePost(event);

            verify(postSearchRepository, times(1)).save(postDocCaptor.capture());
            PostDocument saved = postDocCaptor.getValue();
            assertThat(saved.getId()).isEqualTo("post-new");
            assertThat(saved.getAuthorId()).isEqualTo("user-new");
            assertThat(saved.getContent()).isEqualTo("New content");
        }
    }

    @Nested
    @DisplayName("deletePost()")
    class DeletePostTests {

        @Test
        @DisplayName("delete post removes from index")
        void deletePost_success() {
            indexService.deletePost("post-123");

            verify(postSearchRepository, times(1)).deleteById("post-123");
        }

        @Test
        @DisplayName("deletePost handles exception silently")
        void deletePost_exception_silentFail() {
            doThrow(new RuntimeException("Delete failed"))
                    .when(postSearchRepository).deleteById(anyString());

            indexService.deletePost("post-fail");

            verify(postSearchRepository, times(1)).deleteById("post-fail");
        }
    }

    @Nested
    @DisplayName("User indexing")
    class UserIndexingTests {

        @Test
        @DisplayName("indexOrUpdateUser saves or updates user document")
        void indexOrUpdateUser_save() {
            UserUpdatedEvent event = UserUpdatedEvent.builder()
                    .userId("user-123")
                    .displayName("Farmer John")
                    .bio("I grow vegetables")
                    .avatarUrl("https://example.com/avatar.jpg")
                    .build();

            when(userSearchRepository.save(any(UserDocument.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            indexService.indexOrUpdateUser(event);

            verify(userSearchRepository, times(1)).save(userDocCaptor.capture());
            UserDocument saved = userDocCaptor.getValue();
            assertThat(saved.getId()).isEqualTo("user-123");
            assertThat(saved.getDisplayName()).isEqualTo("Farmer John");
            assertThat(saved.getBio()).isEqualTo("I grow vegetables");
            assertThat(saved.getAvatarUrl()).isEqualTo("https://example.com/avatar.jpg");
        }

        @Test
        @DisplayName("deleteUser removes user from index")
        void deleteUser_success() {
            indexService.deleteUser("user-456");

            verify(userSearchRepository, times(1)).deleteById("user-456");
        }

        @Test
        @DisplayName("deleteUser handles exception silently")
        void deleteUser_exception_silentFail() {
            doThrow(new RuntimeException("Delete failed"))
                    .when(userSearchRepository).deleteById(anyString());

            indexService.deleteUser("user-fail");

            verify(userSearchRepository, times(1)).deleteById("user-fail");
        }
    }
}
