package com.backend.community_service.service;

import com.backend.community_service.dto.AuthorInfo;
import com.backend.community_service.entity.Vote;
import com.backend.community_service.event.PostVotedEvent;
import com.backend.community_service.event.CommentVotedEvent;
import com.backend.community_service.exception.AppException;
import com.backend.community_service.exception.ErrorCode;
import com.backend.community_service.repository.CommentRepository;
import com.backend.community_service.repository.PostRepository;
import com.backend.community_service.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoteService {

    private final VoteRepository voteRepo;
    private final PostRepository postRepo;
    private final CommentRepository commentRepo;
    private final OutboxService outboxService;
    private final AuthorCacheService authorCache;

    @Transactional
    public Short vote(UUID userId, UUID targetId, Vote.TargetType targetType, short value) {
        if (value != 1 && value != -1) {
            throw new AppException(ErrorCode.INVALID_VOTE_VALUE);
        }

        // Fix: chỉ dùng FOR UPDATE query — vừa lock vừa xác nhận target tồn tại.
        if (targetType == Vote.TargetType.POST) {
            postRepo.findByIdAndIsDeletedFalseForUpdate(targetId)
                    .orElseThrow(() -> new AppException(ErrorCode.VOTE_TARGET_NOT_FOUND));
        } else {
            commentRepo.findByIdAndIsDeletedFalseForUpdate(targetId)
                    .orElseThrow(() -> new AppException(ErrorCode.VOTE_TARGET_NOT_FOUND));
        }

        Optional<Vote> existing = voteRepo.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType);

        if (existing.isEmpty()) {
            // Chưa vote → tạo mới
            voteRepo.save(Vote.builder()
                    .userId(userId)
                    .targetId(targetId)
                    .targetType(targetType)
                    .value(value)
                    .build());

            // Publish post.voted event for new upvote on posts, comment.voted for comments
            if (value == 1) {
                if (targetType == Vote.TargetType.POST) {
                    publishPostVoteEvent(userId, targetId);
                } else {
                    publishCommentVoteEvent(userId, targetId);
                }
            }

            log.debug("Vote created | userId={} targetId={} value={}", userId, targetId, value);
            return value;

        } else {
            Vote current = existing.get();

            if (current.getValue() == value) {
                // Same direction → remove (toggle off)
                // Do NOT publish event when removing vote
                voteRepo.delete(current);
                log.debug("Vote removed | userId={} targetId={}", userId, targetId);
                return null;
            } else {
                // Opposite direction → change vote
                current.setValue(value);
                voteRepo.save(current);

                // Publish post.voted event ONLY if changing TO upvote (value = 1)
                if (value == 1) {
                    if (targetType == Vote.TargetType.POST) {
                        publishPostVoteEvent(userId, targetId);
                    } else {
                        publishCommentVoteEvent(userId, targetId);
                    }
                }

                log.debug("Vote changed | userId={} targetId={} newValue={}", userId, targetId, value);
                return value;
            }
        }
    }

    private void publishPostVoteEvent(UUID userId, UUID targetId) {
        postRepo.findAuthorIdById(targetId).ifPresent(postAuthorId -> {
            // Không notify nếu tự upvote bài của mình
            if (!postAuthorId.equals(userId)) {
                AuthorInfo actor = authorCache.getAuthor(userId);
                outboxService.save("post.voted", targetId,
                        PostVotedEvent.builder()
                                .postId(targetId.toString())
                                .postAuthorId(postAuthorId.toString())
                                .actorId(userId.toString())
                                .actorName(actor.getDisplayName())
                                .value((short) 1)
                                .build());
            }
        });
    }

    private void publishCommentVoteEvent(UUID userId, UUID targetId) {
        commentRepo.findByIdWithAuthorAndPost(targetId).ifPresent(comment -> {
            UUID commentAuthorId = comment.getAuthorId();
            UUID postId = comment.getPostId();
            // Không notify nếu tự upvote comment của mình
            if (!commentAuthorId.equals(userId)) {
                AuthorInfo actor = authorCache.getAuthor(userId);
                outboxService.save("comment.voted", targetId,
                        CommentVotedEvent.builder()
                                .commentId(targetId.toString())
                                .commentAuthorId(commentAuthorId.toString())
                                .postId(postId.toString())
                                .actorId(userId.toString())
                                .actorName(actor.getDisplayName())
                                .value((short) 1)
                                .build());
            }
        });
    }

    // Compensation method: Delete all votes for a post (called on saga failure)
    @Transactional
    public void deleteVoteSaga(UUID targetId) {
        voteRepo.deleteByTargetIdAndTargetType(targetId, Vote.TargetType.POST);
        log.info("Compensation executed: all votes deleted for postId={}", targetId);
    }
}
