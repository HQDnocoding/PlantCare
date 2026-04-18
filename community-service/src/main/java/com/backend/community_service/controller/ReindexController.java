package com.backend.community_service.controller;

import com.backend.community_service.dto.AuthorInfo;
import com.backend.community_service.entity.Post;
import com.backend.community_service.event.PostCreatedEvent;
import com.backend.community_service.repository.PostRepository;
import com.backend.community_service.service.AuthorCacheService;
import com.backend.community_service.service.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/reindex")
@RequiredArgsConstructor
@Slf4j
public class ReindexController {

    private final PostRepository postRepo;
    private final OutboxService outboxService;
    private final AuthorCacheService authorCache;

    @PostMapping("/posts")
    @Transactional
    public ResponseEntity<String> reindexAllPosts() {
        // Dùng đúng method có sẵn trong PostRepository
        List<Post> posts = postRepo.findAll()
                .stream()
                .filter(p -> !p.isDeleted())
                .toList();

        // Batch load authors - tránh N+1 problem
        List<UUID> authorIds = posts.stream()
                .map(Post::getAuthorId)
                .distinct()
                .toList();
        Map<UUID, AuthorInfo> authors = authorCache.getAuthors(authorIds);

        int count = 0;
        for (Post post : posts) {
            AuthorInfo author = authors.getOrDefault(post.getAuthorId(), AuthorInfo.unknown(post.getAuthorId()));
            outboxService.save("post.created", post.getId(),
                    PostCreatedEvent.builder()
                            .postId(post.getId().toString())
                            .authorId(post.getAuthorId().toString())
                            .authorName(author.getDisplayName())
                            .content(post.getContent())
                            .tags(new ArrayList<>(post.getTags()))
                            .createdAt(post.getCreatedAt())
                            .score(post.getScore())
                            .build());
            count++;
        }

        log.info("Queued {} posts for reindex", count);
        return ResponseEntity.ok("Queued " + count + " posts for reindex");
    }
}