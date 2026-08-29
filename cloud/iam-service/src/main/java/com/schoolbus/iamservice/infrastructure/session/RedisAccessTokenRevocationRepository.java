package com.schoolbus.iamservice.infrastructure.session;

import com.schoolbus.iamservice.application.authentication.AccessTokenRevocationRepository;
import com.schoolbus.iamservice.infrastructure.security.jwt.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Repository
public class RedisAccessTokenRevocationRepository
        implements AccessTokenRevocationRepository {

    public static final String KEY_PREFIX = "schoolbus:auth:revoked-before:";
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    local current = redis.call('GET', KEYS[1])
                    local candidate = tonumber(ARGV[1])
                    if current and tonumber(current) > candidate then
                        candidate = tonumber(current)
                    end
                    redis.call('SET', KEYS[1], candidate, 'PX', ARGV[2])
                    return candidate
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final Duration timeToLive;

    public RedisAccessTokenRevocationRepository(
            StringRedisTemplate redisTemplate,
            JwtProperties jwtProperties
    ) {
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate,
                "redisTemplate must not be null"
        );
        JwtProperties properties = Objects.requireNonNull(
                jwtProperties,
                "jwtProperties must not be null"
        );
        this.timeToLive = properties.accessTokenTtl().plus(CLOCK_SKEW);
    }

    @Override
    public void revokeIssuedBefore(String subject, Instant revokedAt) {
        String validatedSubject = requireText(subject, "subject");
        Instant validatedRevokedAt = Objects.requireNonNull(
                revokedAt,
                "revokedAt must not be null"
        );
        redisTemplate.execute(
                REVOKE_SCRIPT,
                List.of(KEY_PREFIX + validatedSubject),
                Long.toString(validatedRevokedAt.toEpochMilli()),
                Long.toString(timeToLive.toMillis())
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
