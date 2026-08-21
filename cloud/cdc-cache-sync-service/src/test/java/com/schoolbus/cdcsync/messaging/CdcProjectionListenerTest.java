package com.schoolbus.cdcsync.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.schoolbus.cdcsync.config.CacheProjectionProperties;
import com.schoolbus.cdcsync.event.ConsumedEventRecordedEvent;
import com.schoolbus.cdcsync.event.TripCacheInvalidationEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CdcProjectionListenerTest {

    private final StringRedisTemplate redisTemplate =
            mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations =
            mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final CacheProjectionProperties properties =
            new CacheProjectionProperties(
                    "school-bus:transport:bookable-trips",
                    "school-bus:mq:consumed",
                    Duration.ofDays(30)
            );
    private CdcProjectionListener listener;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        listener = new CdcProjectionListener(
                redisTemplate,
                objectMapper,
                properties,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldDeleteTripListSoNextReadRebuildsCacheAsideValue() throws Exception {
        TripCacheInvalidationEvent event = new TripCacheInvalidationEvent(
                "cdc-1",
                "school_bus_platform",
                "transport_trip",
                "UPDATE",
                Instant.parse("2026-08-21T03:40:00Z"),
                "binlog.000001",
                123L
        );

        listener.invalidateTripCache(objectMapper.writeValueAsBytes(event));

        verify(redisTemplate).delete("school-bus:transport:bookable-trips");
    }

    @Test
    void shouldProjectCommittedConsumedEventAsDoneWithTtl() throws Exception {
        ConsumedEventRecordedEvent event = new ConsumedEventRecordedEvent(
                "cdc-2",
                "payment-refund",
                "event-42",
                Instant.parse("2026-08-21T03:39:59Z"),
                Instant.parse("2026-08-21T03:40:00Z"),
                "binlog.000001",
                456L
        );

        listener.projectConsumedEvent(objectMapper.writeValueAsBytes(event));

        verify(valueOperations).set(
                "school-bus:mq:consumed:payment-refund:event-42",
                "DONE",
                Duration.ofDays(30)
        );
    }
}
