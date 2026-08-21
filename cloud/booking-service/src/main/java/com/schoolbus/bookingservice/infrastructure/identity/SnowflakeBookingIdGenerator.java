package com.schoolbus.bookingservice.infrastructure.identity;

import com.schoolbus.bookingservice.domain.order.BookingId;
import com.schoolbus.bookingservice.domain.order.BookingIdGenerator;
import com.schoolbus.bookingservice.shared.infrastructure.identity.SnowflakeIdGenerator;

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
