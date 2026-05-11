package com.backend.scan_service.config;

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

import com.backend.scan_service.constants.CacheNames;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig {

        @Value("${cache.scan-result-ttl:3600}")
        private long scanResultTtlSeconds;

        @Value("${cache.author-ttl:300}")
        private long authorTtlSeconds;

        @Bean
        public RedisCacheManager cacheManager(
                        RedisConnectionFactory connectionFactory,
                        ObjectMapper objectMapper) {

                ObjectMapper redisMapper = objectMapper.copy()
                                .activateDefaultTyping(
                                                objectMapper.getPolymorphicTypeValidator(),
                                                ObjectMapper.DefaultTyping.NON_FINAL,
                                                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);

                GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisMapper);

                RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .prefixCacheNameWith("scan:")
                                .serializeKeysWith(SerializationPair
                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(SerializationPair
                                                .fromSerializer(serializer));

                Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                                CacheNames.AUTHOR, defaults.entryTtl(Duration.ofSeconds(authorTtlSeconds)),
                                "scan-result", defaults.entryTtl(Duration.ofSeconds(scanResultTtlSeconds)));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaults)
                                .withInitialCacheConfigurations(cacheConfigs)
                                .build();
        }
}
