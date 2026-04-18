package com.backend.community_service.service;

import com.backend.community_service.dto.AuthorInfo;
import com.backend.community_service.entity.Vote;
import com.backend.community_service.event.PostVotedEvent;
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

    /**
     * Vote logic (giống Reddit):
     * - Chưa vote → thêm vote mới
     * - Đã vote cùng chiều → xóa vote (toggle off)
     * - Đã vote ngược chiều → đổi vote
     *
     * Pessimistic locking (SELECT FOR UPDATE) để serialize concurrent votes.
     *
     * Fix Bug #9: bỏ validateTargetExists() — trước đây SELECT target 2 lần:
     * 1. validateTargetExists() → SELECT (check tồn tại)
     * 2. findByIdAndIsDeletedFalseForUpdate() → SELECT FOR UPDATE (lock)
     * Giờ chỉ còn 1 query: FOR UPDATE đã có orElseThrow() → tiết kiệm 1 DB
     * round-trip.
     *
     * @return vote value hiện tại sau khi xử lý (null = đã remove vote)
     */
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
            log.debug("Vote created | userId={} targetId={} value={}", userId, targetId, value);
            return value;

        } else {
            Vote current = existing.get();

            if (current.getValue() == value) {
                // Cùng chiều → remove (toggle off)
                voteRepo.delete(current);
                if (targetType == Vote.TargetType.POST && value == 1) {
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
                                            .value(value)
                                            .build());
                        }
                    });
                }
                log.debug("Vote removed | userId={} targetId={}", userId, targetId);
                return null;
            } else {
                // Ngược chiều → đổi vote
                current.setValue(value);
                voteRepo.save(current);
                log.debug("Vote changed | userId={} targetId={} newValue={}", userId, targetId, value);
                return value;
            }
        }
    }
}
