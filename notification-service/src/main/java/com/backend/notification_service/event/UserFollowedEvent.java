package com.backend.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowedEvent {
    private String followerId;
    private String followerName;
    private String followerAvatar;
    private String followingId;
}
