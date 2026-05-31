package com.backend.search_service.config;

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

import com.backend.search_service.constants.CacheNames;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

        @Value("${cache.search-result-ttl:600}")
        private long searchResultTtlSeconds;

        @Value("${cache.author-ttl:300}")
        private long authorTtlSeconds;

        @Bean
        public RedisCacheManager cacheManager(
                        RedisConnectionFactory connectionFactory,
                        ObjectMapper objectMapper) {

                // ObjectMapper with DefaultTyping for polymorphic deserialization
                ObjectMapper redisMapper = objectMapper.copy()
                                .activateDefaultTyping(
                                                objectMapper.getPolymorphicTypeValidator(),
                                                ObjectMapper.DefaultTyping.NON_FINAL,
                                                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);

                GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisMapper);

                // Default configuration for all caches
                RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .prefixCacheNameWith("search:")
                                .serializeKeysWith(SerializationPair
                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(SerializationPair
                                                .fromSerializer(serializer));

                // Per-cache TTL configuration
                Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                                CacheNames.AUTHOR, defaults.entryTtl(Duration.ofSeconds(authorTtlSeconds)),
                                "search-result", defaults.entryTtl(Duration.ofSeconds(searchResultTtlSeconds)));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaults)
                                .withInitialCacheConfigurations(cacheConfigs)
                                .build();
        }
}
