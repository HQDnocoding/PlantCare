package com.backend.auth.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topics.user-created:user.created}")
    private String userCreatedTopic;

    @Value("${app.kafka.topics.user-deleted:user.deleted}")
    private String userDeletedTopic;

    @Value("${app.kafka.partitions:3}")
    private int partitions;

    @Value("${app.kafka.replicas:1}")
    private int replicas;

    @Bean
    public NewTopic userCreatedTopic() {
        return TopicBuilder.name(userCreatedTopic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic userDeletedTopic() {
        return TopicBuilder.name(userDeletedTopic).partitions(partitions).replicas(replicas).build();
    }
}
