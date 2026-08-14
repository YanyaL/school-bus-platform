package com.schoolbus.payment.infrastructure.persistence;

import com.schoolbus.payment.application.refund.ConsumedEventRepository;
import com.schoolbus.shared.application.messaging.ConsumedEventStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Repository
@Profile("!test")
public class MyBatisConsumedEventRepository
        implements ConsumedEventRepository, ConsumedEventStore {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final ConsumedEventMapper mapper;

    public MyBatisConsumedEventRepository(ConsumedEventMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public boolean exists(String consumerName, String eventId) {
        return mapper.exists(
                requireText(consumerName, "consumerName"),
                requireText(eventId, "eventId")
        ) > 0;
    }

    @Override
    public boolean insertIfAbsent(
            String consumerName,
            String eventId,
            Instant consumedAt
    ) {
        return mapper.insertIfAbsent(
                requireText(consumerName, "consumerName"),
                requireText(eventId, "eventId"),
                LocalDateTime.ofInstant(
                        Objects.requireNonNull(
                                consumedAt,
                                "consumedAt must not be null"
                        ),
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
