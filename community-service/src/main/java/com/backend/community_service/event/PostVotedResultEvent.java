package com.backend.community_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PostVotedResultEvent {
    private String postId;
    private boolean success;
    private String error;
}
