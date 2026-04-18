package com.backend.scan_service.config;

import org.springframework.context.annotation.Configuration;

/**
 * Kafka topic configuration for scan-service.
 * Scan-service does not produce Kafka events currently.
 * Topics are created and managed by other services.
 */
@Configuration
public class KafkaTopicConfig {
    // Scan-service consumes no Kafka events at the moment
    // This class is kept for future extensibility
}
