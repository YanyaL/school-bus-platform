package com.schoolbus.bookingservice.infrastructure.persistence;

import com.schoolbus.bookingservice.shared.application.messaging.ConsumedEventCache;
import com.schoolbus.bookingservice.shared.application.messaging.ConsumedEventStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Repository
@Profile("!test")
public class MyBatisConsumedEventRepository implements ConsumedEventStore {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final ConsumedEventMapper mapper;
    private final ConsumedEventCache cache;

    public MyBatisConsumedEventRepository(
            ConsumedEventMapper mapper,
            ConsumedEventCache cache
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
    }

    @Override
    public boolean exists(String consumerName, String eventId) {
        String normalizedConsumerName = requireText(consumerName, "consumerName");
        String normalizedEventId = requireText(eventId, "eventId");
        return cache.contains(normalizedConsumerName, normalizedEventId)
                || mapper.exists(normalizedConsumerName, normalizedEventId) > 0;
    }

    @Override
    public boolean insertIfAbsent(
            String consumerName,
            String eventId,
            Instant consumedAt
    ) {
        String normalizedConsumerName = requireText(consumerName, "consumerName");
        String normalizedEventId = requireText(eventId, "eventId");
        if (cache.contains(normalizedConsumerName, normalizedEventId)) {
            return false;
        }
        return mapper.insertIfAbsent(
                normalizedConsumerName,
                normalizedEventId,
                LocalDateTime.ofInstant(
                        Objects.requireNonNull(consumedAt, "consumedAt must not be null"),
                        DATABASE_ZONE
                )
        ) == 1;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
