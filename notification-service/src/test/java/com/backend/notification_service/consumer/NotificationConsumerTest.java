package com.backend.notification_service.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.backend.notification_service.event.CommentEvent;
import com.backend.notification_service.event.FollowEvent;
import com.backend.notification_service.event.ReplyEvent;
import com.backend.notification_service.event.VoteEvent;
import com.backend.notification_service.service.IdempotencyService;
import com.backend.notification_service.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationConsumer Tests")
class NotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    private final int partition = 0;
    private final long offset = 123L;
    private final String messageId = "follow-0-123";

    // Test UUIDs
    private final String followerId = "550e8400-e29b-41d4-a716-446655440000";
    private final String followingId = "550e8400-e29b-41d4-a716-446655440001";
    private final String actorId = "550e8400-e29b-41d4-a716-446655440002";
    private final String authorId = "550e8400-e29b-41d4-a716-446655440003";
    private final String postId = "550e8400-e29b-41d4-a716-446655440004";
    private final String commentId = "550e8400-e29b-41d4-a716-446655440005";
    private final String parentAuthorId = "550e8400-e29b-41d4-a716-446655440006";

    @Nested
    @DisplayName("onFollow")
    class OnFollowTests {

        @Test
        @DisplayName("Should skip duplicate follow event")
        void onFollow_duplicateSkipped() throws JsonProcessingException {
            // Given
            String payload = "{\"followerId\":\"" + followerId + "\",\"followingId\":\"" + followingId
                    + "\",\"followerName\":\"John\"}";
            when(idempotencyService.isProcessed("notification-consumer", messageId)).thenReturn(true);

            // When
            notificationConsumer.onFollow(payload, partition, offset);

            // Then
            verify(idempotencyService).isProcessed("notification-consumer", messageId);
            verify(objectMapper, never()).readValue(any(String.class), eq(FollowEvent.class));
            verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any(), any());
            verify(idempotencyService, never()).markAsProcessed(any(), any());
        }

        @Test
        @DisplayName("Should process follow event successfully")
        void onFollow_success() throws JsonProcessingException {
            // Given
            String payload = "{\"followerId\":\"" + followerId + "\",\"followingId\":\"" + followingId
                    + "\",\"followerName\":\"John\"}";
            FollowEvent followEvent = FollowEvent.builder()
                    .followerId(followerId)
                    .followingId(followingId)
                    .followerName("John")
                    .build();

            when(idempotencyService.isProcessed("notification-consumer", messageId)).thenReturn(false);
            when(objectMapper.readValue(payload, FollowEvent.class)).thenReturn(followEvent);
            doNothing().when(notificationService).createAndSend(any(), any(), any(), any(), any(), any(), any(), any());

            // When
            notificationConsumer.onFollow(payload, partition, offset);

            // Then
            verify(idempotencyService).isProcessed("notification-consumer", messageId);
            verify(objectMapper).readValue(payload, FollowEvent.class);
            verify(notificationService).createAndSend(
                    UUID.fromString(followingId), // userId
                    "FOLLOW", // type
                    "Người theo dõi mới", // title
                    "John đã theo dõi bạn", // body
                    UUID.fromString(followerId), // actorId
                    "John", // actorName
                    UUID.fromString(followerId), // targetId
                    "USER" // targetType
            );
            verify(idempotencyService).markAsProcessed("notification-consumer", messageId);
        }

        @Test
        @DisplayName("Should handle malformed JSON payload")
        void onFollow_malformedJson() throws JsonProcessingException {
            // Given
            String payload = "invalid-json";
            when(idempotencyService.isProcessed("notification-consumer", messageId)).thenReturn(false);
            when(objectMapper.readValue(payload, FollowEvent.class))
                    .thenThrow(new JsonProcessingException("Invalid JSON") {
                    });

            // When
            notificationConsumer.onFollow(payload, partition, offset);

            // Then
            verify(idempotencyService).isProcessed("notification-consumer", messageId);
            verify(objectMapper).readValue(payload, FollowEvent.class);
            verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any(), any());
            verify(idempotencyService, never()).markAsProcessed(any(), any());
        }

        @Test
        @DisplayName("Should re-throw runtime exceptions")
        void onFollow_runtimeException() throws JsonProcessingException {
            // Given
            String payload = "{\"followerId\":\"" + followerId + "\",\"followingId\":\"" + followingId
                    + "\",\"followerName\":\"John\"}";
            FollowEvent followEvent = FollowEvent.builder()
                    .followerId(followerId)
                    .followingId(followingId)
                    .followerName("John")
                    .build();

            when(idempotencyService.isProcessed("notification-consumer", messageId)).thenReturn(false);
            when(objectMapper.readValue(payload, FollowEvent.class)).thenReturn(followEvent);
            doThrow(new RuntimeException("Database error"))
                    .when(notificationService).createAndSend(any(), any(), any(), any(), any(), any(), any(), any());

            // When & Then
            assertThatThrownBy(() -> notificationConsumer.onFollow(payload, partition, offset))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to process user.followed");

            verify(idempotencyService).isProcessed("notification-consumer", messageId);
            verify(notificationService).createAndSend(any(), any(), any(), any(), any(), any(), any(), any());
            verify(idempotencyService, never()).markAsProcessed(any(), any());
        }
    }

    @Nested
    @DisplayName("onComment")
    class OnCommentTests {

        @Test
        @DisplayName("Should process comment event successfully")
        void onComment_success() throws JsonProcessingException {
            // Given
            String payload = "{\"actorId\":\"" + actorId + "\",\"postAuthorId\":\"" + authorId + "\",\"postId\":\""
                    + postId + "\",\"actorName\":\"Jane\"}";
            CommentEvent commentEvent = CommentEvent.builder()
                    .actorId(actorId)
                    .postAuthorId(authorId)
                    .postId(postId)
                    .actorName("Jane")
                    .build();

            String messageId = "comment-0-123";
            when(idempotencyService.isProcessed("notification-consumer", messageId)).thenReturn(false);
            when(objectMapper.readValue(payload, CommentEvent.class)).thenReturn(commentEvent);
            doNothing().when(notificationService).createAndSend(any(), any(), any(), any(), any(), any(), any(), any());

            // When
            notificationConsumer.onComment(payload, partition, offset);

            // Then
            verify(notificationService).createAndSend(
                    UUID.fromString(authorId), // userId
                    "COMMENT", // type
                    "Bình luận mới", // title
                    "Jane đã bình luận bài viết của bạn", // body
                    UUID.fromString(actorId), // actorId
                    "Jane", // actorName
                    UUID.fromString(postId), // targetId
                    "POST" // targetType
            );
            verify(idempotencyService).markAsProcessed("notification-consumer", messageId);
        }
    }

    @Nested
    @DisplayName("onVote")
    class OnVoteTests {

        @Test
        @DisplayName("Should process upvote event")
        void onVote_upvote() throws JsonProcessingException {
            // Given
            String payload = "{\"actorId\":\"" + actorId + "\",\"postAuthorId\":\"" + authorId + "\",\"postId\":\""
                    + postId + "\",\"actorName\":\"Bob\",\"value\":1}";
            VoteEvent voteEvent = VoteEvent.builder()
                    .actorId(actorId)
                    .postAuthorId(authorId)
                    .postId(postId)
                    .actorName("Bob")
                    .value(1)
                    .build();

            String messageId = "vote-0-123";
            when(idempotencyService.isProcessed("notification-consumer", messageId)).thenReturn(false);
            when(objectMapper.readValue(payload, VoteEvent.class)).thenReturn(voteEvent);
            doNothing().when(notificationService).createAndSend(any(), any(), any(), any(), any(), any(), any(), any());

            // When
            notificationConsumer.onVote(payload, partition, offset);

            // Then
            verify(notificationService).createAndSend(
                    UUID.fromString(authorId), // userId
                    "VOTE", // type
                    "Upvote mới", // title
                    "Bob đã upvote bài viết của bạn", // body
                    UUID.fromString(actorId), // actorId
                    "Bob", // actorName
                    UUID.fromString(postId), // targetId
                    "POST" // targetType
            );
            verify(idempotencyService).markAsProcessed("notification-consumer", messageId);
        }

        @Test
        @DisplayName("Should ignore downvote event")
        void onVote_downvoteIgnored() throws JsonProcessingException {
            // Given
            String payload = "{\"actorId\":\"" + actorId + "\",\"postAuthorId\":\"" + authorId + "\",\"postId\":\""
                    + postId + "\",\"actorName\":\"Bob\",\"value\":-1}";
            VoteEvent voteEvent = VoteEvent.builder()
                    .actorId(actorId)
                    .postAuthorId(authorId)
                    .postId(postId)
                    .actorName("Bob")
                    .value(-1)
                    .build();

            String messageId = "vote-0-123";
            when(idempotencyService.isProcessed("notification-consumer", messageId)).thenReturn(false);
            when(objectMapper.readValue(payload, VoteEvent.class)).thenReturn(voteEvent);

            // When
            notificationConsumer.onVote(payload, partition, offset);

            // Then
            verify(notificationService, never()).createAndSend(any(), any(), any(), any(), any(), any(), any(), any());
            verify(idempotencyService, never()).markAsProcessed(any(), any());
        }
    }

    @Nested
    @DisplayName("onReply")
    class OnReplyTests {

        @Test
        @DisplayName("Should process reply event successfully")
        void onReply_success() throws JsonProcessingException {
            // Given
            String payload = "{\"actorId\":\"" + actorId + "\",\"parentCommentAuthorId\":\"" + parentAuthorId
                    + "\",\"commentId\":\"" + commentId + "\",\"actorName\":\"Alice\"}";
            ReplyEvent replyEvent = ReplyEvent.builder()
                    .actorId(actorId)
                    .parentCommentAuthorId(parentAuthorId)
                    .commentId(commentId)
                    .actorName("Alice")
                    .build();

            String messageId = "reply-0-123";
            when(idempotencyService.isProcessed("notification-consumer", messageId)).thenReturn(false);
            when(objectMapper.readValue(payload, ReplyEvent.class)).thenReturn(replyEvent);
            doNothing().when(notificationService).createAndSend(any(), any(), any(), any(), any(), any(), any(), any());

            // When
            notificationConsumer.onReply(payload, partition, offset);

            // Then
            verify(notificationService).createAndSend(
                    UUID.fromString(parentAuthorId), // userId
                    "REPLY", // type
                    "Có người trả lời bình luận", // title
                    "Alice đã trả lời bình luận của bạn", // body
                    UUID.fromString(actorId), // actorId
                    "Alice", // actorName
                    UUID.fromString(commentId), // targetId
                    "COMMENT" // targetType
            );
            verify(idempotencyService).markAsProcessed("notification-consumer", messageId);
        }
    }
}