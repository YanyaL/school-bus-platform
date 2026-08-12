package com.schoolbus.transport.infrastructure.identity;

import com.schoolbus.shared.infrastructure.identity.SnowflakeIdGenerator;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripIdGenerator;

import java.util.Objects;

public final class SnowflakeTripIdGenerator implements TripIdGenerator {

    private final SnowflakeIdGenerator idGenerator;

    public SnowflakeTripIdGenerator(
            SnowflakeIdGenerator idGenerator
    ) {
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "idGenerator must not be null"
        );
    }

    @Override
    public TripId nextId() {
        return TripId.of(idGenerator.nextId());
    }
}
