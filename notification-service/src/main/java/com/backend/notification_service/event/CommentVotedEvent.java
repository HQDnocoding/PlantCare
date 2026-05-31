package com.backend.notification_service.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentVotedEvent {
    @JsonProperty("comment_id")
    private String commentId;

    @JsonProperty("comment_author_id")
    private String commentAuthorId;

    @JsonProperty("post_id")
    private String postId;

    @JsonProperty("actor_id")
    private String actorId;

    @JsonProperty("actor_name")
    private String actorName;

    @JsonProperty("value")
    private short value;  // +1 or -1
}
