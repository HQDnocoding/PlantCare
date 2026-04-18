package com.backend.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowEvent {
    private String followerId;
    private String followerName;
    private String followerAvatar;
    private String followingId; // người được follow = nhận notification
}