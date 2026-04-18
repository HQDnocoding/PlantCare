package com.backend.community_service.service;

import com.backend.community_service.dto.AuthorInfo;
import com.backend.community_service.dto.CommentResponse;
import com.backend.community_service.dto.CursorPage;
import com.backend.community_service.entity.Comment;
import com.backend.community_service.entity.Vote;
import com.backend.community_service.event.CommentRepliedEvent;
import com.backend.community_service.event.PostCommentedEvent;
import com.backend.community_service.exception.AppException;
import com.backend.community_service.exception.ErrorCode;
import com.backend.community_service.repository.CommentRepository;
import com.backend.community_service.repository.PostRepository;
import com.backend.community_service.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

        private final CommentRepository commentRepo;
        private final PostRepository postRepo;
        private final VoteRepository voteRepo;
        private final AuthorCacheService authorCache;
        private final OutboxService outboxService;

        @Value("${feed.default-page-size:20}")
        private int defaultPageSize;

        // ── Get comments of a post ────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public CursorPage<CommentResponse> getComments(UUID postId, Instant cursor,
                        int size, UUID requesterId) {
                assertPostExists(postId);
                int pageSize = Math.min(size, defaultPageSize);
                List<Comment> comments = commentRepo.findTopLevelByPostId(
                                postId, cursor, PageRequest.of(0, pageSize + 1));
                return buildCursorPage(comments, pageSize, requesterId);
        }

        // ── Get replies of a comment ──────────────────────────────────────────────

        @Transactional(readOnly = true)
        public CursorPage<CommentResponse> getReplies(UUID commentId, Instant cursor,
                        int size, UUID requesterId) {
                int pageSize = Math.min(size, defaultPageSize);
                List<Comment> replies = commentRepo.findRepliesByParentId(
                                commentId, cursor, PageRequest.of(0, pageSize + 1));
                return buildCursorPage(replies, pageSize, requesterId);
        }

        // ── Create top-level comment ──────────────────────────────────────────────

        @Transactional
        public CommentResponse createComment(UUID postId, UUID authorId, String content) {
                assertPostExists(postId);
                Comment comment = Comment.builder()
                                .postId(postId)
                                .authorId(authorId)
                                .content(content.trim())
                                .build();
                commentRepo.save(comment);
                postRepo.findAuthorIdById(postId).ifPresent(postAuthorId -> {
                        // Không notify nếu tự comment bài của mình
                        if (!postAuthorId.equals(authorId)) {
                                AuthorInfo actor = authorCache.getAuthor(authorId);
                                outboxService.save("post.commented", comment.getId(),
                                                PostCommentedEvent.builder()
                                                                .commentId(comment.getId().toString())
                                                                .postId(postId.toString())
                                                                .postAuthorId(postAuthorId.toString())
                                                                .actorId(authorId.toString())
                                                                .actorName(actor.getDisplayName())
                                                                .contentPreview(content.length() > 50
                                                                                ? content.substring(0, 50) + "..."
                                                                                : content)
                                                                .build());
                        }
                });
                log.info("Comment created | commentId={} postId={}", comment.getId(), postId);
                return CommentResponse.from(comment, authorCache.getAuthor(authorId), null);
        }

        // ── Create reply (1 cấp) ─────────────────────────────────────────────────

        @Transactional
        public CommentResponse createReply(UUID postId, UUID parentCommentId,
                        UUID authorId, String content) {
                assertPostExists(postId);

                Comment parent = commentRepo.findByIdAndIsDeletedFalse(parentCommentId)
                                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

                // Đảm bảo parent thuộc đúng post
                if (!parent.getPostId().equals(postId)) {
                        throw new AppException(ErrorCode.COMMENT_NOT_BELONG_TO_POST);
                }

                // Chỉ cho reply vào top-level comment, không reply vào reply
                if (!parent.isTopLevel()) {
                        throw new AppException(ErrorCode.REPLY_DEPTH_EXCEEDED);
                }

                Comment reply = Comment.builder()
                                .postId(postId)
                                .authorId(authorId)
                                .parentId(parentCommentId)
                                .content(content.trim())
                                .build();
                commentRepo.save(reply);
                if (!parent.getAuthorId().equals(authorId)) {
                        AuthorInfo actor = authorCache.getAuthor(authorId);
                        outboxService.save("comment.replied", reply.getId(),
                                        CommentRepliedEvent.builder()
                                                        .commentId(reply.getId().toString())
                                                        .postId(postId.toString())
                                                        .parentCommentAuthorId(parent.getAuthorId().toString())
                                                        .actorId(authorId.toString())
                                                        .actorName(actor.getDisplayName())
                                                        .contentPreview(content.length() > 50
                                                                        ? content.substring(0, 50) + "..."
                                                                        : content)
                                                        .build());
                }
                log.info("Reply created | replyId={} parentId={}", reply.getId(), parentCommentId);
                return CommentResponse.from(reply, authorCache.getAuthor(authorId), null);
        }

        // ── Update comment ────────────────────────────────────────────────────────

        @Transactional
        public CommentResponse updateComment(UUID commentId, UUID requesterId, String content) {
                Comment comment = commentRepo.findByIdAndIsDeletedFalse(commentId)
                                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

                if (!comment.getAuthorId().equals(requesterId)) {
                        throw new AppException(ErrorCode.FORBIDDEN);
                }

                comment.setContent(content.trim());
                comment.setEdited(true);
                commentRepo.save(comment);
                Short myVote = voteRepo
                                .findByUserIdAndTargetIdAndTargetType(requesterId, commentId, Vote.TargetType.COMMENT)
                                .map(v -> (short) v.getValue())
                                .orElse(null);
                log.info("Comment updated | commentId={}", commentId);
                return CommentResponse.from(comment, authorCache.getAuthor(comment.getAuthorId()), myVote);
        }

        // ── Delete comment ────────────────────────────────────────────────────────

        @Transactional
        public void deleteComment(UUID commentId, UUID requesterId) {
                Comment comment = commentRepo.findByIdAndIsDeletedFalse(commentId)
                                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

                if (!comment.getAuthorId().equals(requesterId)) {
                        throw new AppException(ErrorCode.FORBIDDEN);
                }

                comment.softDelete();
                commentRepo.save(comment);
                log.info("Comment soft-deleted | commentId={}", commentId);
        }

        // ── Helpers ───────────────────────────────────────────────────────────────

        private void assertPostExists(UUID postId) {
                postRepo.findByIdAndIsDeletedFalse(postId)
                                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
        }

        private CursorPage<CommentResponse> buildCursorPage(List<Comment> comments,
                        int pageSize,
                        UUID requesterId) {
                boolean hasMore = comments.size() > pageSize;
                List<Comment> page = hasMore ? comments.subList(0, pageSize) : comments;

                if (page.isEmpty())
                        return CursorPage.empty();

                // Batch fetch authors
                List<UUID> authorIds = page.stream().map(Comment::getAuthorId).distinct().toList();
                Map<UUID, AuthorInfo> authors = authorCache.getAuthors(authorIds);

                // Batch fetch my votes
                Map<UUID, Short> myVotes = new HashMap<>();
                if (requesterId != null) {
                        List<UUID> commentIds = page.stream().map(Comment::getId).toList();
                        voteRepo.findByUserIdAndTargetTypeAndTargetIdIn(requesterId, Vote.TargetType.COMMENT,
                                        commentIds)
                                        .forEach(v -> myVotes.put(v.getTargetId(), (short) v.getValue()));
                }

                List<CommentResponse> responses = page.stream()
                                .map(c -> CommentResponse.from(
                                                c,
                                                authors.getOrDefault(c.getAuthorId(),
                                                                AuthorInfo.unknown(c.getAuthorId())),
                                                myVotes.get(c.getId())))
                                .toList();

                String nextCursor = null;
                if (hasMore) {
                        long timestamp = page.get(page.size() - 1).getCreatedAt().toEpochMilli();
                        byte[] bytes = ByteBuffer.allocate(Long.BYTES).putLong(timestamp).array();
                        nextCursor = Base64.getEncoder().encodeToString(bytes);
                }

                return CursorPage.of(responses, nextCursor);
        }
}
