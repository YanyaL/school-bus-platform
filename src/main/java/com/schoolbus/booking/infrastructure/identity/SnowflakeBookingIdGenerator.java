package com.schoolbus.booking.infrastructure.identity;

import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingIdGenerator;
import com.schoolbus.shared.infrastructure.identity.SnowflakeIdGenerator;

import java.util.Objects;

public final class SnowflakeBookingIdGenerator
        implements BookingIdGenerator {

    private final SnowflakeIdGenerator idGenerator;

    public SnowflakeBookingIdGenerator(
            SnowflakeIdGenerator idGenerator
    ) {
        this.idGenerator = Objects.requireNonNull(
                idGenerator,
                "idGenerator must not be null"
        );
    }

    @Override
    public BookingId nextId() {
        return BookingId.of(idGenerator.nextId());
    }
}
