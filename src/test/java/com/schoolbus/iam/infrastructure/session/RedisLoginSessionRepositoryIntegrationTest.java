package com.schoolbus.iam.infrastructure.session;

import com.schoolbus.iam.application.authentication.LoginSession;
import com.schoolbus.iam.application.authentication.LoginSessionRepository;
import com.schoolbus.shared.config.TimeConfiguration;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DataRedisTest
@Import({
        RedisLoginSessionRepository.class,
        TimeConfiguration.class
})
@ActiveProfiles("redis-integration-test")
class RedisLoginSessionRepositoryIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            ).withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(REDIS_PORT)
        );
    }

    @Autowired
    private LoginSessionRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        Set<String> keys = redisTemplate.keys(
                "school-bus:login-session:*"
        );
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void shouldSaveAndFindSessionByBothIndexes() {
        LoginSession session = session(
                "session-001",
                "refresh-hash-001"
        );

        repository.save(session);

        assertThat(
                repository.findBySessionId("session-001")
        ).contains(session);
        assertThat(
                repository.findByRefreshTokenHash(
                        "refresh-hash-001"
                )
        ).contains(session);

        Long sessionTtl = redisTemplate.getExpire(
                RedisLoginSessionRepository.sessionKey(
                        "session-001"
                ),
                TimeUnit.SECONDS
        );
        Long refreshIndexTtl = redisTemplate.getExpire(
                RedisLoginSessionRepository.refreshKey(
                        "refresh-hash-001"
                ),
                TimeUnit.SECONDS
        );
        assertThat(sessionTtl).isPositive();
        assertThat(refreshIndexTtl).isPositive();
    }

    @Test
    void shouldDeleteSessionAndRefreshIndexTogether() {
        repository.save(
                session("session-002", "refresh-hash-002")
        );

        repository.deleteBySessionId("session-002");

        assertThat(
                repository.findBySessionId("session-002")
        ).isEmpty();
        assertThat(
                repository.findByRefreshTokenHash(
                        "refresh-hash-002"
                )
        ).isEmpty();
    }

    @Test
    void shouldRemoveOldRefreshIndexWhenSessionRotates() {
        repository.save(
                session("session-003", "old-refresh-hash")
        );
        LoginSession rotated = session(
                "session-003",
                "new-refresh-hash"
        );

        repository.save(rotated);

        assertThat(
                repository.findByRefreshTokenHash(
                        "old-refresh-hash"
                )
        ).isEmpty();
        assertThat(
                repository.findByRefreshTokenHash(
                        "new-refresh-hash"
                )
        ).contains(rotated);
    }

    @Test
    void shouldAllowOnlyOneAtomicRefreshTokenReplacement() {
        LoginSession original = session(
                "session-004",
                "original-refresh-hash"
        );
        repository.save(original);
        LoginSession firstReplacement = session(
                "session-004",
                "first-replacement-hash"
        );
        LoginSession staleConcurrentReplacement = session(
                "session-004",
                "stale-concurrent-hash"
        );

        boolean firstResult = repository.replaceRefreshToken(
                firstReplacement,
                "original-refresh-hash"
        );
        boolean staleConcurrentResult =
                repository.replaceRefreshToken(
                        staleConcurrentReplacement,
                        "original-refresh-hash"
                );

        assertThat(firstResult).isTrue();
        assertThat(staleConcurrentResult).isFalse();
        assertThat(
                repository.findByRefreshTokenHash(
                        "original-refresh-hash"
                )
        ).isEmpty();
        assertThat(
                repository.findByRefreshTokenHash(
                        "first-replacement-hash"
                )
        ).contains(firstReplacement);
        assertThat(
                repository.findByRefreshTokenHash(
                        "stale-concurrent-hash"
                )
        ).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownSession() {
        Optional<LoginSession> result = repository
                .findBySessionId("missing-session");

        assertThat(result).isEmpty();
    }

    private LoginSession session(
            String sessionId,
            String refreshTokenHash
    ) {
        Instant createdAt = Instant.now();
        return new LoginSession(
                sessionId,
                UserId.of(1000001L),
                refreshTokenHash,
                createdAt,
                createdAt.plusSeconds(300)
        );
    }
}
