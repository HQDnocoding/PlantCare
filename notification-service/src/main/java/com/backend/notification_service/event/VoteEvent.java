package com.backend.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteEvent {
    private String postId;
    private String postAuthorId; // nhận notification
    private String actorId;
    private String actorName;
    private int value; // +1 hoặc -1
}