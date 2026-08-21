package com.schoolbus.paymentservice.infrastructure.persistence;

import com.schoolbus.paymentservice.application.refund.ConsumedEventCache;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisConsumedEventRepositoryTest {

    private final ConsumedEventMapper mapper = mock(ConsumedEventMapper.class);
    private final ConsumedEventCache cache = mock(ConsumedEventCache.class);
    private final MyBatisConsumedEventRepository repository =
            new MyBatisConsumedEventRepository(mapper, cache);

    @Test
    void shouldUseRedisPositiveProjectionBeforeMySql() {
        when(cache.contains("payment-refund", "event-1")).thenReturn(true);

        assertThat(repository.exists("payment-refund", "event-1")).isTrue();

        verify(mapper, never()).exists("payment-refund", "event-1");
    }

    @Test
    void shouldFallBackToMySqlOnCacheMiss() {
        when(cache.contains("payment-refund", "event-1")).thenReturn(false);
        when(mapper.exists("payment-refund", "event-1")).thenReturn(1);

        assertThat(repository.exists("payment-refund", "event-1")).isTrue();
    }

    @Test
    void shouldSkipDuplicateInsertWhenProjectionIsPresent() {
        when(cache.contains("payment-refund", "event-1")).thenReturn(true);

        assertThat(repository.insertIfAbsent(
                "payment-refund",
                "event-1",
                Instant.parse("2026-08-21T04:00:00Z")
        )).isFalse();
        verify(mapper, never()).insertIfAbsent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
