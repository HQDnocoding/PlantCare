package com.backend.notification_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topics.user-followed:user.followed}")
    private String userFollowedTopic;

    @Value("${app.kafka.topics.post-commented:post.commented}")
    private String postCommentedTopic;

    @Value("${app.kafka.topics.post-voted:post.voted}")
    private String postVotedTopic;

    @Value("${app.kafka.topics.comment-replied:comment.replied}")
    private String commentRepliedTopic;

    @Value("${app.kafka.partitions:3}")
    private int partitions;

    @Value("${app.kafka.replicas:1}")
    private int replicas;

    @Bean
    public NewTopic userFollowedTopic() {
        return TopicBuilder.name(userFollowedTopic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic postCommentedTopic() {
        return TopicBuilder.name(postCommentedTopic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic postVotedTopic() {
        return TopicBuilder.name(postVotedTopic).partitions(partitions).replicas(replicas).build();
    }

    @Bean
    public NewTopic commentRepliedTopic() {
        return TopicBuilder.name(commentRepliedTopic).partitions(partitions).replicas(replicas).build();
    }
}
