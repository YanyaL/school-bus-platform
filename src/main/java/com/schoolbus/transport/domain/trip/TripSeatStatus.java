package com.schoolbus.transport.domain.trip;

import java.util.Objects;

public record TripSeatStatus(
        String seatNumber,
        String status
) {

    public TripSeatStatus {
        Objects.requireNonNull(seatNumber, "seatNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
