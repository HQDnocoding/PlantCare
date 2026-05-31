package com.backend.search_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topics.user-updated:user.updated}")
    private String userUpdatedTopic;

    @Value("${app.kafka.topics.user-deleted:user.deleted}")
    private String userDeletedTopic;

    @Value("${app.kafka.topics.post-created:post.created}")
    private String postCreatedTopic;

    @Value("${app.kafka.topics.post-updated:post.updated}")
    private String postUpdatedTopic;

    @Value("${app.kafka.topics.post-deleted:post.deleted}")
    private String postDeletedTopic;

    @Value("${app.kafka.partitions:3}")
    private int partitions;

    @Value("${app.kafka.replicas:1}")
    private int replicas;

    @Bean
    public NewTopic userUpdatedTopic() {
        return TopicBuilder.name(userUpdatedTopic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic userDeletedTopic() {
        return TopicBuilder.name(userDeletedTopic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic postCreatedTopic() {
        return TopicBuilder.name(postCreatedTopic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic postUpdatedTopic() {
        return TopicBuilder.name(postUpdatedTopic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic postDeletedTopic() {
        return TopicBuilder.name(postDeletedTopic).partitions(partitions).replicas(replicas).build();
    }
}
