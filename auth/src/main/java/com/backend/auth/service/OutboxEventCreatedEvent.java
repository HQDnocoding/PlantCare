package com.backend.auth.service;

import java.util.UUID;

public record OutboxEventCreatedEvent(UUID outboxEventId) {
}