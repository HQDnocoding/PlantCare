package com.backend.search_service.service;

import com.backend.search_service.dto.PostSearchResult;
import com.backend.search_service.dto.SearchRequest;
import com.backend.search_service.dto.SearchResponse;
import com.backend.search_service.dto.UserSearchResult;
import com.backend.search_service.exception.AppException;
import com.backend.search_service.repository.PostSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

        private final PostSearchRepository postRepo;
        // private final UserSearchRepository userRepo;

        public SearchResponse search(SearchRequest request, int page) {
                int size = request.getSize();
                // Lấy size+1 để check hasMore
                Pageable pageable = PageRequest.of(page, size + 1);
                String type = request.getType();

                List<PostSearchResult> posts = List.of();
                List<UserSearchResult> users = List.of();
                boolean hasMore = false;

                if ("all".equals(type) || "posts".equals(type)) {
                        try {
                                log.debug("Searching posts | keyword={} | page={} | size={}", request.getQ(), page,
                                                size);

                                List<PostSearchResult> rawPosts = postRepo.searchByKeyword(request.getQ(), pageable)
                                                .stream()
                                                .map(doc -> PostSearchResult.builder()
                                                                .postId(doc.getId())
                                                                .authorId(doc.getAuthorId())
                                                                .authorName(doc.getAuthorName())
                                                                .content(doc.getContent())
                                                                .tags(doc.getTags())
                                                                .score(doc.getScore())
                                                                .createdAt(doc.getCreatedAt())
                                                                .build())
                                                .toList();

                                log.info("Search results | keyword={} | found={} results", request.getQ(),
                                                rawPosts.size());

                                hasMore = rawPosts.size() > size;
                                posts = hasMore ? rawPosts.subList(0, size) : rawPosts;

                        } catch (AppException e) {
                                log.warn("Elasticsearch index 'posts' not found yet or query failed | error={}",
                                                e.getMessage());
                        } catch (Exception e) {
                                log.error("Search error | keyword={} | error={}", request.getQ(), e.getMessage(), e);
                        }
                }

                return SearchResponse.builder()
                                .posts(posts)
                                .users(users)
                                .hasMore(hasMore)
                                .build();
        }
}