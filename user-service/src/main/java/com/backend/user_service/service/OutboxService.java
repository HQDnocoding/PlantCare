package com.backend.user_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.backend.user_service.entity.OutboxEvent;
import com.backend.user_service.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.lang.Assert;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(String topic, UUID aggregateId, Object payload) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(aggregateId, "aggregateId must not be null");
        Assert.notNull(payload, "payload must not be null");

        try {
            outboxRepo.save(OutboxEvent.builder()
                    .topic(topic)
                    .aggregateId(aggregateId.toString())
                    .payload(objectMapper.writeValueAsString(payload))
                    .processed(false)
                    .build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event for topic: " + topic, e);
        }
    }
}
