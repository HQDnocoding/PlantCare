package com.backend.community_service.service;

import com.backend.community_service.dto.AuthorInfo;
import com.backend.community_service.dto.CursorPage;
import com.backend.community_service.dto.PostCreateRequest;
import com.backend.community_service.dto.PostResponse;
import com.backend.community_service.entity.Post;
import com.backend.community_service.entity.Vote;
import com.backend.community_service.event.PostCreatedEvent;
import com.backend.community_service.event.PostDeletedEvent;
import com.backend.community_service.event.PostUpdatedEvent;
import com.backend.community_service.exception.AppException;
import com.backend.community_service.exception.ErrorCode;
import com.backend.community_service.repository.PostRepository;
import com.backend.community_service.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepo;
    private final VoteRepository voteRepo;
    private final AuthorCacheService authorCache;
    private final FirebaseStorageService storageService;
    private final OutboxService outboxService;

    @Value("${feed.default-page-size:20}")
    private int defaultPageSize;

    @Value("${feed.max-images-per-post:4}")
    private int maxImages;

    @Value("${feed.max-tags-per-post:5}")
    private int maxTags;

    @Value("${feed.max-tag-length:32}")
    private int maxTagLength;

    // ── Create post ───────────────────────────────────────────────────────────

    @Transactional
    public PostResponse createPost(UUID authorId, PostCreateRequest request) {
        // 1. Validate
        validateTags(request.getTags());

        // 2. Upload images (nếu có)
        List<String> imageUrls = new ArrayList<>();
        List<String> imagePaths = new ArrayList<>();

        try {
            if (request.getImages() != null && !request.getImages().isEmpty()) {
                validateImages(request.getImages());
                for (MultipartFile img : request.getImages()) {
                    String[] result = storageService.uploadPostImage(authorId, img);
                    imageUrls.add(result[0]);
                    imagePaths.add(result[1]);
                    log.debug("Uploaded image to Firebase: {}", result[1]);
                }
            }

            // 3. Save Entity
            Post post = Post.builder()
                    .authorId(authorId)
                    .content(request.getContent().trim())
                    .imageUrls(imageUrls.toArray(new String[0]))
                    .imagePaths(imagePaths.toArray(new String[0]))
                    .tags(normalizeTags(request.getTags()))
                    .build();

            try {
                postRepo.save(post);
                log.info("Post created successfully with ID: {}", post.getId());
            } catch (Exception dbException) {
                // Database save failed → rollback Firebase uploads
                log.error("Database save failed, rolling back Firebase uploads", dbException);

                for (String path : imagePaths) {
                    try {
                        storageService.deleteFile(path);
                        log.info("Rolled back Firebase file: {}", path);
                    } catch (Exception deleteException) {
                        log.error("Failed to roll back Firebase file: {}", path, deleteException);
                        // Continue rolling back other files
                    }
                }

                throw new AppException(ErrorCode.DATABASE_ERROR,
                        "Failed to save post. Firebase uploads have been rolled back: " + dbException.getMessage());
            }

            // 4. Publish event via outbox (transactional with post save)
            AuthorInfo author = authorCache.getAuthor(authorId);
            outboxService.save("post.created", post.getId(),
                    PostCreatedEvent.builder()
                            .postId(post.getId().toString())
                            .authorId(authorId.toString())
                            .authorName(author.getDisplayName())
                            .content(post.getContent())
                            .tags(new ArrayList<>(post.getTags()))
                            .createdAt(post.getCreatedAt())
                            .score(post.getScore())
                            .build());

            log.info("Post created and event published. PostId: {}, AuthorId: {}", post.getId(), authorId);
            return PostResponse.from(post, author, null);

        } catch (AppException appException) {
            log.error("Application error while creating post", appException);
            throw appException;
        } catch (Exception unexpectedException) {
            log.error("Unexpected error while creating post", unexpectedException);

            // Attempt emergency rollback of Firebase uploads
            for (String path : imagePaths) {
                try {
                    storageService.deleteFile(path);
                } catch (Exception e) {
                    log.error("Emergency rollback failed for: {}", path, e);
                }
            }

            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred while creating post: " + unexpectedException.getMessage());
        }
    }

    // ── Get single post ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PostResponse getPost(UUID postId, UUID requesterId) {
        Post post = getActivePost(postId);
        AuthorInfo author = authorCache.getAuthor(post.getAuthorId());
        Short myVote = getMyVote(requesterId, postId, Vote.TargetType.POST);
        return PostResponse.from(post, author, myVote);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminPostById(UUID postId) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
        return Map.of(
                "id", post.getId().toString(),
                "authorId", post.getAuthorId().toString(),
                "content", post.getContent(),
                "imageUrls", post.getImageUrls(),
                "tags", post.getTags(),
                "upvoteCount", post.getUpvoteCount(),
                "downvoteCount", post.getDownvoteCount(),
                "commentCount", post.getCommentCount(),
                "isDeleted", post.isDeleted(),
                "createdAt", post.getCreatedAt().toString());
    }

    @Transactional
    public void deleteAllPostsByUser(UUID userId) {
        List<Post> posts = postRepo.findAllActiveByAuthorId(userId);

        if (posts.isEmpty()) {
            log.info("No active posts found for user {}", userId);
            return; // idempotent — retry an toàn
        }

        posts.forEach(post -> {
            // Xóa ảnh Firebase
            if (post.getImagePaths() != null) {
                for (String path : post.getImagePaths()) {
                    storageService.deleteFile(path);
                }
            }
            post.softDelete();

            // Ghi outbox — cùng transaction với soft-delete
            outboxService.save("post.deleted", post.getId(),
                    PostDeletedEvent.builder()
                            .postId(post.getId().toString())
                            .build());
        });

        postRepo.saveAll(posts);
        log.info("Soft-deleted {} posts for user {}", posts.size(), userId);
    }

    // ── Global feed: New ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getGlobalFeedNew(String cursorStr, int size, UUID requesterId) {
        Instant cursor = null;
        if (cursorStr != null && !cursorStr.isBlank()) {
            try {
                // Giải mã Base64 mà Đạt đã encode ở buildCursor
                String decoded = new String(Base64.getDecoder().decode(cursorStr));
                cursor = Instant.ofEpochMilli(Long.parseLong(decoded));
            } catch (Exception e) {
                log.warn("Invalid cursor format: {}", cursorStr);
                // Nếu cursor lỗi, có thể trả về trang đầu tiên hoặc quăng lỗi tùy Đạt
            }
        }

        int pageSize = Math.min(size, defaultPageSize);
        // PageSize + 1 để check hasMore
        List<Post> posts = postRepo.findGlobalFeedNew(cursor, PageRequest.of(0, pageSize + 1));
        return buildCursorPage(posts, pageSize, requesterId, SortType.NEW);
    }

    // ── Global feed: Top ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getGlobalFeedTop(Integer scoreCursor,
            Instant timeCursor,
            int size,
            UUID requesterId) {
        int pageSize = Math.min(size, defaultPageSize);
        List<Post> posts = postRepo.findGlobalFeedTop(scoreCursor, timeCursor, PageRequest.of(0, pageSize + 1));
        return buildCursorPage(posts, pageSize, requesterId, SortType.TOP);
    }

    // ── Following feed ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getFollowingFeed(List<UUID> followingIds,
            Instant cursor,
            int size,
            UUID requesterId) {
        if (followingIds == null || followingIds.isEmpty()) {
            return CursorPage.empty();
        }
        int pageSize = Math.min(size, defaultPageSize);
        List<Post> posts = postRepo.findFollowingFeed(followingIds, cursor, PageRequest.of(0, pageSize + 1));
        return buildCursorPage(posts, pageSize, requesterId, SortType.NEW);
    }

    // ── Filter by tag ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getPostsByTag(String tag,
            Instant cursor,
            int size,
            UUID requesterId) {
        int pageSize = Math.min(size, defaultPageSize);
        List<Post> posts = postRepo.findByTag(tag, cursor, PageRequest.of(0, pageSize + 1));
        return buildCursorPage(posts, pageSize, requesterId, SortType.NEW);
    }

    // ── Update post ───────────────────────────────────────────────────────────

    @Transactional
    public PostResponse updatePost(UUID postId, UUID requesterId,
            String content, List<String> tags) {
        // Fix Bug #6: validate content sau trim — tránh update post với nội dung rỗng
        if (content == null || content.trim().isBlank()) {
            throw new AppException(ErrorCode.POST_CONTENT_REQUIRED);
        }

        Post post = getActivePost(postId);
        assertOwner(post.getAuthorId(), requesterId);
        validateTags(tags);

        post.setContent(content.trim());
        post.getTags().clear();
        post.getTags().addAll(normalizeTags(tags));
        postRepo.save(post);

        AuthorInfo author = authorCache.getAuthor(post.getAuthorId());

        // Fix Bug #8: dùng đúng topic "post.updated" và PostUpdatedEvent thay vì
        // "post.created".
        // Trước đây publish "post.created" khi update → search-service nhận nhầm là bài
        // mới.
        outboxService.save("post.updated", post.getId(),
                PostUpdatedEvent.builder()
                        .postId(post.getId().toString())
                        .authorId(post.getAuthorId().toString())
                        .authorName(author.getDisplayName())
                        .content(post.getContent())
                        .tags(new java.util.HashSet<>(post.getTags()))
                        .updatedAt(java.time.Instant.now())
                        .build());

        return PostResponse.from(post, author, getMyVote(requesterId, postId, Vote.TargetType.POST));
    }

    // ── Admin-only ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Post> getAllPostsForAdmin(int page, int size) {
        return postRepo.findAll(
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("createdAt").descending()));
    }

    @Transactional
    public void deletePostByAdmin(UUID postId) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        if (post.getImagePaths() != null) {
            for (String path : post.getImagePaths()) {
                storageService.deleteFile(path);
            }
        }

        post.softDelete();
        outboxService.save("post.deleted", post.getId(),
                PostDeletedEvent.builder()
                        .postId(post.getId().toString())
                        .build());
        postRepo.save(post);
        log.info("[Admin] Soft-deleted post {}", postId);
    }

    // ── Delete post ───────────────────────────────────────────────────────────

    @Transactional
    public void deletePost(UUID postId, UUID requesterId) {
        Post post = getActivePost(postId);
        assertOwner(post.getAuthorId(), requesterId);

        // Xóa ảnh khỏi Firebase Storage
        if (post.getImagePaths() != null) {
            for (String path : post.getImagePaths()) {
                storageService.deleteFile(path);
            }
        }

        post.softDelete();
        outboxService.save("post.deleted", post.getId(),
                PostDeletedEvent.builder()
                        .postId(post.getId().toString())
                        .build());
        postRepo.save(post);
        log.info("Post soft-deleted | postId={}", postId);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> getMyPosts(UUID userId, String cursorStr, int size) {
        // 1. Decode cursor (Giống logic hàm Global Feed)
        Instant cursor = null;
        if (cursorStr != null && !cursorStr.isBlank()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(cursorStr));
                cursor = Instant.ofEpochMilli(Long.parseLong(decoded));
            } catch (Exception e) {
                log.warn("Invalid cursor for getMyPosts: {}", cursorStr);
            }
        }

        int pageSize = Math.min(size, defaultPageSize);

        // 2. Query DB (Lấy pageSize + 1 để check hasMore)
        List<Post> posts = postRepo.findByAuthorId(userId, cursor, PageRequest.of(0, pageSize + 1));

        // 3. Dùng buildCursorPage để batch fetch Author & Votes
        // requesterId ở đây chính là userId vì mình đang xem bài của chính mình
        return buildCursorPage(posts, pageSize, userId, SortType.NEW);
    }
    // ── Helpers ───────────────────────────────────────────────────────────────

    private Post getActivePost(UUID postId) {
        return postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
    }

    private void assertOwner(UUID ownerId, UUID requesterId) {
        if (!ownerId.equals(requesterId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateTags(List<String> tags) {
        if (tags == null)
            return;
        if (tags.size() > maxTags)
            throw new AppException(ErrorCode.TOO_MANY_TAGS);
        tags.forEach(t -> {
            if (t.length() > maxTagLength)
                throw new AppException(ErrorCode.TAG_TOO_LONG);
        });
    }

    private void validateImages(List<MultipartFile> images) {
        if (images != null && images.size() > maxImages) {
            throw new AppException(ErrorCode.TOO_MANY_IMAGES);
        }
    }

    private Set<String> normalizeTags(List<String> tags) {
        if (tags == null)
            return new HashSet<>();
        return tags.stream()
                .map(String::trim)
                .filter(t -> !t.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private Short getMyVote(UUID userId, UUID targetId, Vote.TargetType type) {
        if (userId == null)
            return null;
        return voteRepo.findByUserIdAndTargetIdAndTargetType(userId, targetId, type)
                .map(v -> (short) v.getValue())
                .orElse(null);
    }

    /**
     * Fetch tất cả author info và vote của requester cho 1 trang posts.
     * Tránh N+1: 1 batch call cho author cache, 1 query cho votes.
     */
    private CursorPage<PostResponse> buildCursorPage(List<Post> posts,
            int pageSize,
            UUID requesterId,
            SortType sortType) {
        boolean hasMore = posts.size() > pageSize;
        List<Post> page = hasMore ? posts.subList(0, pageSize) : posts;

        if (page.isEmpty())
            return CursorPage.empty();

        // Batch fetch author info
        List<UUID> authorIds = page.stream().map(Post::getAuthorId).distinct().toList();
        Map<UUID, AuthorInfo> authors = authorCache.getAuthors(authorIds);

        // Batch fetch my votes
        Map<UUID, Short> myVotes = new HashMap<>();
        if (requesterId != null) {
            List<UUID> postIds = page.stream().map(Post::getId).toList();
            voteRepo.findByUserIdAndTargetTypeAndTargetIdIn(requesterId, Vote.TargetType.POST, postIds)
                    .forEach(v -> myVotes.put(v.getTargetId(), (short) v.getValue()));
        }

        List<PostResponse> responses = page.stream()
                .map(p -> PostResponse.from(
                        p,
                        authors.getOrDefault(p.getAuthorId(), AuthorInfo.unknown(p.getAuthorId())),
                        myVotes.get(p.getId())))
                .toList();

        // Build next cursor dựa theo sort type
        String nextCursor = hasMore ? buildCursor(page.get(page.size() - 1), sortType) : null;
        return CursorPage.of(responses, nextCursor);
    }

    private String buildCursor(Post lastPost, SortType sortType) {
        String raw = switch (sortType) {
            case NEW -> lastPost.getCreatedAt().toEpochMilli() + "_" + lastPost.getId();
            case TOP -> lastPost.getScore() + "_" + lastPost.getCreatedAt().toEpochMilli() + "_" + lastPost.getId();
            default -> lastPost.getCreatedAt().toEpochMilli() + "";
        };
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    enum SortType {
        NEW, TOP, HOT
    }

}
