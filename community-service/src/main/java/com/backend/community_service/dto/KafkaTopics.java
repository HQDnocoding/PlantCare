package com.backend.community_service.dto;

public final class KafkaTopics {
    private KafkaTopics() {
    }

    public static final String POST_CREATED = "post.created";
    public static final String POST_UPDATED = "post.updated";
    public static final String POST_DELETED = "post.deleted";
}
