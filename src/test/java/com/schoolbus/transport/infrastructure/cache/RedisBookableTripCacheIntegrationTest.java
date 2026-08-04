package com.schoolbus.transport.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.transport.application.trip.BookableTripCache;
import com.schoolbus.transport.application.trip.BookableTripView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DataRedisTest
@Import({
        RedisBookableTripCache.class,
        RedisBookableTripCacheIntegrationTest.JacksonConfiguration.class
})
@ActiveProfiles("redis-integration-test")
class RedisBookableTripCacheIntegrationTest {

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
        registry.add(
                "school-bus.trip-list-cache.ttl",
                () -> "PT1M"
        );
    }

    @Autowired
    private BookableTripCache cache;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.delete(RedisBookableTripCache.CACHE_KEY);
    }

    @Test
    void shouldReplaceAndReadOrderedTripListWithTtl() {
        List<BookableTripView> trips = List.of(
                trip(1001L, "2026-08-04T09:00:00Z"),
                trip(1002L, "2026-08-04T10:00:00Z")
        );

        cache.replaceAll(trips);

        assertThat(cache.findAll()).contains(trips);
        assertThat(redisTemplate.getExpire(
                RedisBookableTripCache.CACHE_KEY,
                TimeUnit.SECONDS
        )).isPositive();
    }

    @Test
    void shouldCacheAnEmptyResultInsteadOfRepeatedlyQueryingMySql() {
        cache.replaceAll(List.of());

        assertThat(cache.findAll()).contains(List.of());
        assertThat(redisTemplate.opsForList().range(
                RedisBookableTripCache.CACHE_KEY,
                0,
                -1
        )).containsExactly(RedisBookableTripCache.EMPTY_MARKER);
    }

    @Test
    void shouldEvictCachedList() {
        cache.replaceAll(List.of(
                trip(1001L, "2026-08-04T09:00:00Z")
        ));

        cache.evict();

        assertThat(cache.findAll()).isEmpty();
    }

    private BookableTripView trip(
            long id,
            String departureTime
    ) {
        Instant departure = Instant.parse(departureTime);
        return new BookableTripView(
                id,
                "11111111-1111-1111-1111-" + String.format(
                        "%012d",
                        id
                ),
                3001L,
                2001L,
                departure,
                departure.minusSeconds(1800),
                new BigDecimal("5.00")
        );
    }

    @TestConfiguration
    static class JacksonConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
