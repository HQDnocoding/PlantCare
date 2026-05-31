package com.backend.auth.service;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backend.auth.entity.OutboxEvent;
import com.backend.auth.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, UUID aggregateId, Object payload) {
        save(topic, aggregateId, payload, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, UUID aggregateId, Object payload, boolean retryable) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(aggregateId, "aggregateId must not be null");
        Assert.notNull(payload, "payload must not be null");

        try {
            OutboxEvent outboxEvent = outboxRepo.save(OutboxEvent.create(
                    topic,
                    aggregateId.toString(),
                    objectMapper.writeValueAsString(payload),
                    retryable));
            applicationEventPublisher.publishEvent(new OutboxEventCreatedEvent(outboxEvent.getId()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event for topic: " + topic, e);
        }
    }
}
