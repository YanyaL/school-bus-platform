package com.schoolbus.transport.application.trip;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TripSeatMapView(
        String tripNumber,
        Instant bookingDeadline,
        List<TripSeatStatusView> seats
) {

    public TripSeatMapView {
        Objects.requireNonNull(
                tripNumber,
                "tripNumber must not be null"
        );
        Objects.requireNonNull(
                bookingDeadline,
                "bookingDeadline must not be null"
        );
        seats = seats == null ? List.of() : List.copyOf(seats);
    }
}
