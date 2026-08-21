package com.schoolbus.shared.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisConsumedEventCacheTest {

    private final StringRedisTemplate redisTemplate =
            mock(StringRedisTemplate.class);
    private final RedisConsumedEventCache cache = new RedisConsumedEventCache(
            redisTemplate,
            "school-bus:mq:consumed"
    );

    @Test
    void shouldBuildDeterministicProjectionKey() {
        when(redisTemplate.hasKey(
                "school-bus:mq:consumed:payment-refund:event-1"
        )).thenReturn(true);

        assertThat(cache.contains("payment-refund", "event-1")).isTrue();
    }

    @Test
    void shouldReturnMissSoRepositoryCanFallBackWhenRedisIsUnavailable() {
        when(redisTemplate.hasKey(
                "school-bus:mq:consumed:payment-refund:event-1"
        )).thenThrow(new RedisConnectionFailureException("offline"));

        assertThat(cache.contains("payment-refund", "event-1")).isFalse();
    }
}
