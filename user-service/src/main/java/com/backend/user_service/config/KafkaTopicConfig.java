package com.backend.user_service.config;

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

    @Value("${app.kafka.topics.user-followed:user.followed}")
    private String userFollowedTopic;

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
    public NewTopic userFollowedTopic() {
        return TopicBuilder.name(userFollowedTopic).partitions(partitions).replicas(replicas).build();
    }
}
