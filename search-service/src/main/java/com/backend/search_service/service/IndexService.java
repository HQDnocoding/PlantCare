package com.backend.search_service.service;

import com.backend.search_service.document.PostDocument;
import com.backend.search_service.document.UserDocument;
import com.backend.search_service.event.PostCreatedEvent;
import com.backend.search_service.event.PostUpdatedEvent;
import com.backend.search_service.event.UserUpdatedEvent;
import com.backend.search_service.repository.PostSearchRepository;
import com.backend.search_service.repository.UserSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexService {

    private final PostSearchRepository postRepo;
    private final UserSearchRepository userRepo;

    public void indexPost(PostCreatedEvent e) {
        try {
            PostDocument doc = PostDocument.builder()
                    .id(e.getPostId())
                    .authorId(e.getAuthorId())
                    .authorName(e.getAuthorName())
                    .content(e.getContent())
                    .tags(e.getTags())
                    .createdAt(e.getCreatedAt())
                    .score(e.getScore())
                    .build();

            log.debug("Indexing post | postId={} | content={} | tags={}",
                    e.getPostId(), e.getContent(), e.getTags());

            postRepo.save(doc);
            log.info("Post indexed | postId={}", e.getPostId());
        } catch (Exception ex) {
            log.error("Failed to index post | postId={} error={}", e.getPostId(), ex.getMessage(), ex);
        }
    }

    public void updatePost(PostUpdatedEvent e) {
        try {
            // Upsert — handles out-of-order Kafka events where update arrives before create
            PostDocument doc = postRepo.findById(e.getPostId())
                    .orElseGet(() -> {
                        log.warn("Post not found in index, creating new document | postId={}", e.getPostId());
                        return PostDocument.builder()
                                .id(e.getPostId())
                                .authorId(e.getAuthorId())
                                .build();
                    });

            doc.setContent(e.getContent());
            doc.setTags(e.getTags());
            postRepo.save(doc);
            log.info("Post updated | postId={}", e.getPostId());
        } catch (Exception ex) {
            log.error("Failed to update post | postId={} error={}", e.getPostId(), ex.getMessage());
        }
    }

    public void deletePost(String postId) {
        try {
            postRepo.deleteById(postId);
            log.info("Post deleted | postId={}", postId);
        } catch (Exception e) {
            log.error("Failed to delete post | postId={} error={}", postId, e.getMessage());
        }
    }

    public void indexOrUpdateUser(UserUpdatedEvent e) {
        try {
            userRepo.save(UserDocument.builder()
                    .id(e.getUserId())
                    .displayName(e.getDisplayName())
                    .bio(e.getBio())
                    .avatarUrl(e.getAvatarUrl())
                    .build());
            log.info("User indexed | userId={}", e.getUserId());
        } catch (Exception ex) {
            log.error("Failed to index user | userId={} error={}", e.getUserId(), ex.getMessage());
        }
    }

    public void deleteUser(String userId) {
        try {
            userRepo.deleteById(userId);
            log.info("User deleted | userId={}", userId);
        } catch (Exception e) {
            log.error("Failed to delete user | userId={} error={}", userId, e.getMessage());
        }
    }
}