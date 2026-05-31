package com.backend.community_service.consumer;

import com.backend.community_service.entity.ProcessedMessage;
import com.backend.community_service.event.PostIndexedResultEvent;
import com.backend.community_service.event.PostNotifiedResultEvent;
import com.backend.community_service.repository.ProcessedMessageRepository;
import com.backend.community_service.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostCreationResultConsumer {

    private final PostService postService;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ObjectMapper objectMapper;

    // Track saga states: postId -> { indexed: result, notified: result }
    private final ConcurrentHashMap<String, SagaState> sagaStates = new ConcurrentHashMap<>();

    @KafkaListener(topics = "post.indexed.result", groupId = "community-service")
    @Transactional
    public void onIndexedResult(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        String messageKey = "post.indexed.result:" + partition + ":" + offset;
        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate post.indexed.result skipped: {}", messageKey);
            return;
        }

        try {
            PostIndexedResultEvent event = objectMapper.readValue(payload, PostIndexedResultEvent.class);
            log.info("[PostCreationResultConsumer] Received post.indexed.result | postId={} | success={}",
                    event.getPostId(), event.isSuccess());

            SagaState state = sagaStates.computeIfAbsent(event.getPostId(), k -> new SagaState());
            state.indexedResult = event;
            state.lastUpdate = System.currentTimeMillis();

            checkAndFinalizeSaga(event.getPostId(), state);
            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());

        } catch (Exception e) {
            log.error("Failed to process post.indexed.result | error={}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "post.notified.result", groupId = "community-service")
    @Transactional
    public void onNotifiedResult(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        String messageKey = "post.notified.result:" + partition + ":" + offset;
        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate post.notified.result skipped: {}", messageKey);
            return;
        }

        try {
            PostNotifiedResultEvent event = objectMapper.readValue(payload, PostNotifiedResultEvent.class);
            log.info("[PostCreationResultConsumer] Received post.notified.result | postId={} | success={}",
                    event.getPostId(), event.isSuccess());

            SagaState state = sagaStates.computeIfAbsent(event.getPostId(), k -> new SagaState());
            state.notifiedResult = event;
            state.lastUpdate = System.currentTimeMillis();

            checkAndFinalizeSaga(event.getPostId(), state);
            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());

        } catch (Exception e) {
            log.error("Failed to process post.notified.result | error={}", e.getMessage(), e);
        }
    }

    private void checkAndFinalizeSaga(String postId, SagaState state) {
        // If both results received, finalize
        if (state.indexedResult != null && state.notifiedResult != null) {
            if (state.indexedResult.isSuccess() && state.notifiedResult.isSuccess()) {
                log.info("[PostCreationResultConsumer] Post creation saga completed successfully | postId={}", postId);
            } else {
                // Compensation: delete post if any step failed
                log.warn("[PostCreationResultConsumer] Post creation saga failed, deleting post | postId={} | " +
                        "indexedSuccess={} | notifiedSuccess={}",
                        postId, state.indexedResult.isSuccess(), state.notifiedResult.isSuccess());
                compensatePostDeletion(postId, state);
            }
            sagaStates.remove(postId);
        }
    }

    private void compensatePostDeletion(String postId, SagaState state) {
        try {
            postService.deletePostSaga(UUID.fromString(postId));
            log.info("[PostCreationResultConsumer] Compensation: deleted post {} due to saga failure", postId);
        } catch (Exception e) {
            log.error("[PostCreationResultConsumer] Compensation failed for post {} | error={}", postId, e.getMessage(),
                    e);
        }
    }

    // Helper class to track saga state
    private static class SagaState {
        PostIndexedResultEvent indexedResult;
        PostNotifiedResultEvent notifiedResult;
        long lastUpdate;

        boolean isExpired() {
            return System.currentTimeMillis() - lastUpdate > TimeUnit.MINUTES.toMillis(5);
        }
    }
}
