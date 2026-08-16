package com.schoolbus.transportquery.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schoolbus.transportquery.application.BookableTripView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisBookableTripCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private RedisBookableTripCache newCache() {
        return new RedisBookableTripCache(
                redisTemplate,
                objectMapper,
                Duration.ofMinutes(1)
        );
    }

    @Test
    void shouldReturnEmptyOptionalOnMiss() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(RedisBookableTripCache.CACHE_KEY, 0, -1))
                .thenReturn(List.of());

        assertThat(newCache().findAll()).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForEmptyMarker() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(RedisBookableTripCache.CACHE_KEY, 0, -1))
                .thenReturn(List.of(RedisBookableTripCache.EMPTY_MARKER));

        Optional<List<BookableTripView>> result = newCache().findAll();

        assertThat(result).isPresent();
        assertThat(result.orElseThrow()).isEmpty();
    }

    @Test
    void shouldDeserializeCachedTrips() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        BookableTripView trip = sampleTrip();
        when(listOperations.range(RedisBookableTripCache.CACHE_KEY, 0, -1))
                .thenReturn(List.of(objectMapper.writeValueAsString(trip)));

        assertThat(newCache().findAll().orElseThrow()).containsExactly(trip);
    }

    @Test
    void shouldEvictCorruptCacheAndFail() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range(RedisBookableTripCache.CACHE_KEY, 0, -1))
                .thenReturn(List.of("{not-json"));

        assertThatThrownBy(() -> newCache().findAll())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("corrupt");
        verify(redisTemplate).delete(RedisBookableTripCache.CACHE_KEY);
    }

    @Test
    void shouldReplaceAllAtomicallyIncludingEmptyMarker() {
        newCache().replaceAll(List.of());

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(RedisBookableTripCache.CACHE_KEY)),
                eq("60000"),
                eq(RedisBookableTripCache.EMPTY_MARKER)
        );
    }

    private static BookableTripView sampleTrip() {
        return new BookableTripView(
                1001L,
                "11111111-1111-1111-1111-111111111111",
                3001L,
                2001L,
                Instant.parse("2026-08-05T09:00:00Z"),
                Instant.parse("2026-08-05T08:00:00Z"),
                new BigDecimal("5.00")
        );
    }
}
