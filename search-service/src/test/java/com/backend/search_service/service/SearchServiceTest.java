package com.backend.search_service.service;

import com.backend.search_service.document.PostDocument;
import com.backend.search_service.dto.PostSearchResult;
import com.backend.search_service.dto.SearchRequest;
import com.backend.search_service.dto.SearchResponse;
import com.backend.search_service.exception.AppException;
import com.backend.search_service.repository.PostSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private PostSearchRepository postSearchRepository;

    @InjectMocks
    private SearchService searchService;

    @Nested
    @DisplayName("search()")
    class SearchTests {

        @Test
        @DisplayName("search posts with keyword and page 0")
        void search_postsWithKeyword_returnsResults() {
            SearchRequest request = new SearchRequest();
            request.setQ("leaf disease");
            request.setType("posts");
            request.setSize(10);

            PostDocument doc1 = PostDocument.builder()
                    .id("post-1")
                    .authorId("user-1")
                    .authorName("Farmer John")
                    .content("How to treat leaf disease")
                    .tags(Set.of("disease", "farming"))
                    .score(5)
                    .createdAt(Instant.now())
                    .build();

            PostDocument doc2 = PostDocument.builder()
                    .id("post-2")
                    .authorId("user-2")
                    .authorName("Agronomist Jane")
                    .content("Best practices for leaf disease prevention")
                    .tags(Set.of("prevention", "leaf"))
                    .score(3)
                    .createdAt(Instant.now().minusSeconds(3600))
                    .build();

            Pageable pageable = PageRequest.of(0, 11);
            when(postSearchRepository.searchByKeyword(eq("leaf disease"), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(doc1, doc2)));

            SearchResponse response = searchService.search(request, 0);

            assertThat(response.getPosts()).hasSize(2);
            assertThat(response.getPosts().get(0).getPostId()).isEqualTo("post-1");
            assertThat(response.getPosts().get(1).getPostId()).isEqualTo("post-2");
            assertThat(response.isHasMore()).isFalse();
        }

        @Test
        @DisplayName("search with pagination — hasMore is true when results exceed size")
        void search_pagination_hasMoreTrue() {
            SearchRequest request = new SearchRequest();
            request.setQ("farming");
            request.setType("posts");
            request.setSize(10);

            // Generate 11 results to indicate hasMore
            List<PostDocument> docs = java.util.stream.IntStream.range(0, 11)
                    .mapToObj(i -> PostDocument.builder()
                            .id("post-" + i)
                            .authorId("author-" + i)
                            .authorName("Author " + i)
                            .content("Content " + i)
                            .tags(Set.of("tag-" + i))
                            .score(i)
                            .createdAt(Instant.now())
                            .build())
                    .toList();

            Pageable pageable = PageRequest.of(0, 11);
            when(postSearchRepository.searchByKeyword(eq("farming"), eq(pageable)))
                    .thenReturn(new PageImpl<>(docs));

            SearchResponse response = searchService.search(request, 0);

            assertThat(response.getPosts()).hasSize(10);
            assertThat(response.isHasMore()).isTrue();
        }

        @Test
        @DisplayName("search on page 1 with offset")
        void search_page1_appliesOffset() {
            SearchRequest request = new SearchRequest();
            request.setQ("pest control");
            request.setType("posts");
            request.setSize(5);

            List<PostDocument> docs = java.util.stream.IntStream.range(5, 10)
                    .mapToObj(i -> PostDocument.builder()
                            .id("post-" + i)
                            .authorId("author-" + i)
                            .authorName("Author " + i)
                            .content("Content " + i)
                            .tags(Set.of("pest"))
                            .score(i)
                            .createdAt(Instant.now())
                            .build())
                    .toList();

            Pageable pageable = PageRequest.of(1, 6);
            when(postSearchRepository.searchByKeyword(eq("pest control"), eq(pageable)))
                    .thenReturn(new PageImpl<>(docs));

            SearchResponse response = searchService.search(request, 1);

            assertThat(response.getPosts()).hasSize(5);
            assertThat(response.getPosts().get(0).getPostId()).isEqualTo("post-5");
        }

        @Test
        @DisplayName("search of type 'all' includes post search")
        void search_typeAll_searchPosts() {
            SearchRequest request = new SearchRequest();
            request.setQ("spring crop");
            request.setType("all");
            request.setSize(20);

            List<PostDocument> docs = List.of(
                    PostDocument.builder()
                            .id("post-1")
                            .authorId("user-1")
                            .authorName("Farmer")
                            .content("Spring crop planting guide")
                            .tags(Set.of("spring", "crop"))
                            .score(10)
                            .createdAt(Instant.now())
                            .build());

            Pageable pageable = PageRequest.of(0, 21);
            when(postSearchRepository.searchByKeyword(eq("spring crop"), eq(pageable)))
                    .thenReturn(new PageImpl<>(docs));

            SearchResponse response = searchService.search(request, 0);

            assertThat(response.getPosts()).hasSize(1);
            assertThat(response.getUsers()).isEmpty();
        }

        @Test
        @DisplayName("search when Elasticsearch index not found returns empty result")
        void search_indexNotFound_returnsEmptyResult() {
            SearchRequest request = new SearchRequest();
            request.setQ("any query");
            request.setType("posts");
            request.setSize(10);

            Pageable pageable = PageRequest.of(0, 11);
            when(postSearchRepository.searchByKeyword(anyString(), eq(pageable)))
                    .thenThrow(new AppException(com.backend.search_service.exception.ErrorCode.INDEX_NOT_FOUND));

            SearchResponse response = searchService.search(request, 0);

            assertThat(response.getPosts()).isEmpty();
            assertThat(response.getUsers()).isEmpty();
            assertThat(response.isHasMore()).isFalse();
        }
    }
}
