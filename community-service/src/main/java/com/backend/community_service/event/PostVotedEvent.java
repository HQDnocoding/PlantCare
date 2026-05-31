
package com.backend.community_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostVotedEvent {
    private String postId;
    private String postAuthorId;
    private String actorId;
    private String actorName;
    private int value;
}