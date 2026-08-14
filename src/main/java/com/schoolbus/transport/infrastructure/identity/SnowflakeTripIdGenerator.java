package com.schoolbus.transport.infrastructure.identity;

import com.schoolbus.shared.infrastructure.identity.SnowflakeIdGenerator;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripIdGenerator;

import java.util.Objects;

public final class SnowflakeTripIdGenerator implements TripIdGenerator {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public SnowflakeTripIdGenerator(
            SnowflakeIdGenerator snowflakeIdGenerator
    ) {
        this.snowflakeIdGenerator = Objects.requireNonNull(
                snowflakeIdGenerator,
                "snowflakeIdGenerator must not be null"
        );
    }

    @Override
    public TripId nextId() {
        return TripId.of(snowflakeIdGenerator.nextId());
    }
}
