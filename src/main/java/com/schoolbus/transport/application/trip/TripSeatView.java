package com.schoolbus.transport.application.trip;

import java.util.Objects;

public record TripSeatView(
        String seatNumber,
        String status
) {

    public TripSeatView {
        Objects.requireNonNull(seatNumber, "seatNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
