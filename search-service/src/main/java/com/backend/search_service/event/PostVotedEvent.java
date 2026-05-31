package com.backend.search_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PostVotedEvent {
    private String postId;
    private String postAuthorId;
    private String actorId;
    private String actorName;
    private short value; // +1 for upvote, -1 for downvote
}
