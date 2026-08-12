package com.schoolbus.transport.application.trip;

import java.util.Objects;

public record TripSeatStatusView(
        String seatNumber,
        String status
) {

    public TripSeatStatusView {
        Objects.requireNonNull(seatNumber, "seatNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
