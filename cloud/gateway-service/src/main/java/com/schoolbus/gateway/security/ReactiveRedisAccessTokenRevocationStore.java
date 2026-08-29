package com.schoolbus.gateway.security;

import com.schoolbus.gateway.config.TokenRevocationProperties;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.Objects;

public class ReactiveRedisAccessTokenRevocationStore
        implements AccessTokenRevocationStore {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String keyPrefix;

    public ReactiveRedisAccessTokenRevocationStore(
            ReactiveStringRedisTemplate redisTemplate,
            TokenRevocationProperties properties
    ) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "redisTemplate must not be null"
        );
        this.keyPrefix = Objects.requireNonNull(
                properties,
                "properties must not be null"
        ).keyPrefix();
    }

    @Override
    public Mono<Long> findRevokedBeforeEpochMilli(String subject) {
        if (subject == null || subject.isBlank()) {
            return Mono.error(
                    new IllegalArgumentException("subject must not be blank")
            );
        }
        return redisTemplate.opsForValue()
                .get(keyPrefix + subject.trim())
                .map(Long::parseLong);
    }
}
