package com.schoolbus.cdcsync.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.cdcsync.config.CacheProjectionProperties;
import com.schoolbus.cdcsync.event.ConsumedEventRecordedEvent;
import com.schoolbus.cdcsync.event.TripCacheInvalidationEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CdcProjectionListener {

    private static final String DONE = "DONE";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProjectionProperties properties;
    private final Counter tripInvalidations;
    private final Counter idempotencyProjections;

    public CdcProjectionListener(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            CacheProjectionProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tripInvalidations = Counter.builder("school_bus_cdc_projection_total")
                .tag("projection", "trip_cache_invalidation")
                .register(meterRegistry);
        this.idempotencyProjections = Counter.builder(
                        "school_bus_cdc_projection_total"
                )
                .tag("projection", "consumed_event")
                .register(meterRegistry);
    }

    @RabbitListener(queues = "${school-bus.cdc.messaging.trip-queue}")
    public void invalidateTripCache(byte[] payload) {
        read(payload, TripCacheInvalidationEvent.class);
        redisTemplate.delete(properties.tripListKey());
        tripInvalidations.increment();
    }

    @RabbitListener(
            queues = "${school-bus.cdc.messaging.consumed-event-queue}"
    )
    public void projectConsumedEvent(byte[] payload) {
        ConsumedEventRecordedEvent event = read(
                payload,
                ConsumedEventRecordedEvent.class
        );
        redisTemplate.opsForValue().set(
                consumedEventKey(event.consumerName(), event.consumedEventId()),
                DONE,
                properties.consumedEventTtl()
        );
        idempotencyProjections.increment();
    }

    private String consumedEventKey(String consumerName, String eventId) {
        return properties.consumedEventKeyPrefix()
                + ":" + consumerName
                + ":" + eventId;
    }

    private <T> T read(byte[] payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "invalid CDC projection message for " + type.getSimpleName(),
                    exception
            );
        }
    }
}
