package com.backend.search_service.controller;

import com.backend.search_service.document.PostDocument;
import com.backend.search_service.repository.PostSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search/internal/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final PostSearchRepository postRepo;

    /**
     * List all indexed posts (for debugging)
     */
    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<PostDocument> posts = postRepo.findAll(PageRequest.of(page, size));

            Map<String, Object> result = new HashMap<>();
            result.put("totalElements", posts.getTotalElements());
            result.put("currentPage", page);
            result.put("pageSize", size);
            result.put("posts", posts.getContent().stream().map(post -> Map.of(
                    "id", post.getId(),
                    "content",
                    post.getContent() == null ? "NULL"
                            : post.getContent().substring(0, Math.min(50, post.getContent().length())),
                    "author", post.getAuthorName(),
                    "tags", post.getTags())).toList());

            log.info("Debug: Listed {} posts from Elasticsearch", posts.getTotalElements());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Debug error listing posts", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "message", "Failed to list posts"));
        }
    }

    /**
     * Test search query
     */
    @GetMapping("/search-test")
    public ResponseEntity<Map<String, Object>> testSearch(@RequestParam String q) {
        try {
            log.info("Debug: Testing search with keyword: {}", q);
            Page<PostDocument> results = postRepo.searchByKeyword(q, PageRequest.of(0, 10));

            Map<String, Object> result = new HashMap<>();
            result.put("keyword", q);
            result.put("found", results.getTotalElements());
            result.put("results", results.getContent().stream().map(post -> Map.of(
                    "id", post.getId(),
                    "content",
                    post.getContent() == null ? "NULL"
                            : post.getContent().substring(0, Math.min(100, post.getContent().length())),
                    "author", post.getAuthorName(),
                    "tags", post.getTags())).toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Debug search error", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "message", "Search test failed"));
        }
    }

    /**
     * Health check for Elasticsearch connection
     */
    @GetMapping("/index-info")
    public ResponseEntity<Map<String, Object>> getIndexInfo() {
        try {
            long documentCount = postRepo.count();
            Map<String, Object> result = new HashMap<>();
            result.put("index", "posts");
            result.put("documentCount", documentCount);
            result.put("status", documentCount > 0 ? "OK" : "EMPTY_INDEX");

            log.info("Debug: Index info - {} documents", documentCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Debug index info error", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", e.getMessage(),
                    "status", "UNREACHABLE"));
        }
    }
}
