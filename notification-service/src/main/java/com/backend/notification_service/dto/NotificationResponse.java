package com.backend.notification_service.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private String type;
    private String title;
    private String body;
    private UUID actorId;
    private String actorName;
    private UUID targetId;
    private String targetType;
    private boolean isRead;
    private Instant createdAt;
}
