package com.backend.community_service.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.backend.community_service.constants.CacheNames;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

    @Value("${cache.author-ttl:300}")
    private long authorTtlSeconds;

    /**
     * RedisCacheManager with per-cache TTL configuration.
     * Uses GenericJackson2JsonRedisSerializer with type info
     * so any object can be cached without a per-type RedisTemplate.
     */
    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        // Copy to avoid mutating the global ObjectMapper bean
        // DefaultTyping embeds class name in JSON — enables correct deserialization
        ObjectMapper redisMapper = objectMapper.copy()
                .activateDefaultTyping(
                        objectMapper.getPolymorphicTypeValidator(),
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisMapper);

        // Default config applies to all caches unless overridden below
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith("community:") // key pattern: community:{cacheName}:{key}
                .serializeKeysWith(SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair
                        .fromSerializer(serializer));

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                CacheNames.AUTHOR, defaults.entryTtl(Duration.ofSeconds(authorTtlSeconds)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}