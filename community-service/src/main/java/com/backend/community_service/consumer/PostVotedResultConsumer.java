package com.backend.community_service.consumer;

import com.backend.community_service.entity.ProcessedMessage;
import com.backend.community_service.event.PostVotedNotifiedResultEvent;
import com.backend.community_service.repository.ProcessedMessageRepository;
import com.backend.community_service.service.VoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostVotedResultConsumer {

    private final ProcessedMessageRepository processedMessageRepository;
    private final VoteService voteService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "post.voted.notified", groupId = "community-service")
    @Transactional
    public void onVotedNotified(@Payload String payload,
            @Header("kafka_receivedPartitionId") int partition,
            @Header("kafka_offset") long offset) {
        String messageKey = "post.voted.notified:" + partition + ":" + offset;
        if (processedMessageRepository.existsByMessageKey(messageKey)) {
            log.debug("Duplicate post.voted.notified skipped: {}", messageKey);
            return;
        }

        try {
            PostVotedNotifiedResultEvent event = objectMapper.readValue(payload,
                    PostVotedNotifiedResultEvent.class);
            log.info("[PostVotedResultConsumer] Received post.voted.notified | postId={} | success={}",
                    event.getPostId(), event.isSuccess());

            processedMessageRepository.save(ProcessedMessage.builder().messageKey(messageKey).build());

            // If notification fails, compensate by deleting the vote
            if (!event.isSuccess()) {
                log.warn("[PostVotedResultConsumer] Vote notification failed for postId={}, triggering compensation",
                        event.getPostId());
                compensateVoteDeletion(event.getPostId());
            } else {
                log.info("[PostVotedResultConsumer] Post voted saga completed successfully | postId={}",
                        event.getPostId());
            }

        } catch (Exception e) {
            log.error("[PostVotedResultConsumer] Failed to process post.voted.notified | error={}",
                    e.getMessage(), e);
        }
    }

    private void compensateVoteDeletion(String postId) {
        try {
            // Delete the vote from community-service (compensation)
            UUID postUUID = UUID.fromString(postId);
            voteService.deleteVoteSaga(postUUID);
            log.info("[PostVotedResultConsumer] Compensation executed: vote deleted for postId={}", postId);
        } catch (Exception e) {
            log.error("[PostVotedResultConsumer] Failed to execute compensation for postId={}", postId, e);
        }
    }
}
