package com.backend.notification_service.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.backend.notification_service.dto.NotificationResponse;
import com.backend.notification_service.entity.Notification;
import com.backend.notification_service.event.PostCreatedEvent;
import com.backend.notification_service.event.CommentVotedEvent;
import com.backend.notification_service.event.UserFollowedEvent;
import com.backend.notification_service.event.CommentRepliedEvent;
import com.backend.notification_service.exception.AppException;
import com.backend.notification_service.exception.ErrorCode;
import com.backend.notification_service.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final FcmService fcmService;
    private final SseService sseService;

    @Transactional
    public void createAndSend(UUID userId, String type, String title,
            String body, UUID actorId, String actorName,
            UUID targetId, String targetType) {
        if (userId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        // 1. Lưu DB
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .body(body)
                .actorId(actorId)
                .actorName(actorName)
                .targetId(targetId)
                .targetType(targetType)
                .isRead(false)
                .build();
        notificationRepo.save(notification);

        NotificationResponse response = toResponse(notification);

        // 2. SSE — nếu app đang mở
        sseService.sendToUser(userId, response);

        // 3. FCM — nếu app đang đóng
        Map<String, String> fcmData = new HashMap<>();
        fcmData.put("type", type != null ? type : "");
        fcmData.put("targetId", targetId != null ? targetId.toString() : "");
        fcmData.put("targetType", targetType != null ? targetType : "");

        try {
            fcmService.sendToUser(userId, title, body, fcmData);
        } catch (AppException e) {
            log.warn("FCM delivery failed for userId={}: {}", userId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepo.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepo.markAllAsRead(userId);
    }

    @Transactional
    public void notifyFollowersOnPostCreation(UUID authorId, PostCreatedEvent event) {
        // TODO: Query followers from user-service via Feign
        // For now, this is a placeholder that will be expanded later
        log.info("Post creation notification triggered | authorId={} | postId={}",
                authorId, event.getPostId());

        // Future implementation will:
        // 1. Call UserService to get list of followers
        // 2. For each follower, call createAndSend() with type="POST"
    }

    @Transactional
    public void notifyPostAuthorOnVote(UUID authorId, UUID actorId,
            com.backend.notification_service.event.PostVotedEvent event) {
        // Notify post author about vote (upvote only, not downvote)
        if (event.getValue() == 1) {
            log.info("Vote notification triggered | authorId={} | actorId={} | postId={}",
                    authorId, actorId, event.getPostId());

            // TODO: Call createAndSend() with type="VOTE"
            // createAndSend(authorId, actorId, event.getPostId(), "VOTE",
            // event.getActorName());
        } else {
            log.debug("Downvote - no notification | authorId={} | actorId={} | postId={}",
                    authorId, actorId, event.getPostId());
        }
    }

    @Transactional
    public void notifyCommentAuthorOnVote(UUID authorId, UUID actorId,
            com.backend.notification_service.event.CommentVotedEvent event) {
        // Notify comment author about vote on their comment
        if (event.getValue() == 1) {
            log.info("Comment vote notification triggered | authorId={} | actorId={} | commentId={}",
                    authorId, actorId, event.getCommentId());

            // TODO: Call createAndSend() with type="COMMENT_VOTE"
        } else {
            log.debug("Downvote on comment - no notification | authorId={} | actorId={} | commentId={}",
                    authorId, actorId, event.getCommentId());
        }
    }

    @Transactional
    public void notifyUserFollowed(UUID followingId, UUID followerId,
            com.backend.notification_service.event.UserFollowedEvent event) {
        log.info("User followed notification triggered | followingId={} | followerId={}",
                followingId, followerId);

        // TODO: Call createAndSend() with type="FOLLOW"
    }

    @Transactional
    public void notifyCommentReplied(UUID parentAuthorId, UUID actorId,
            com.backend.notification_service.event.CommentRepliedEvent event) {
        log.info("Comment replied notification triggered | parentAuthorId={} | actorId={} | commentId={}",
                parentAuthorId, actorId, event.getCommentId());

        // TODO: Call createAndSend() with type="REPLY"
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .actorId(n.getActorId())
                .actorName(n.getActorName())
                .targetId(n.getTargetId())
                .targetType(n.getTargetType())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}