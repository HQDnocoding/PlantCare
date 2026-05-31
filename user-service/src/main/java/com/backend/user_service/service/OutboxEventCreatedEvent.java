package com.backend.user_service.service;

import java.util.UUID;

public record OutboxEventCreatedEvent(UUID outboxEventId) {
}