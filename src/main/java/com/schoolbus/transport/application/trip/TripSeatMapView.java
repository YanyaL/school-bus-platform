package com.schoolbus.transport.application.trip;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TripSeatMapView(
        long tripId,
        Instant bookingDeadline,
        List<TripSeatView> seats
) {

    public TripSeatMapView {
        Objects.requireNonNull(
                bookingDeadline,
                "bookingDeadline must not be null"
        );
        seats = seats == null ? List.of() : List.copyOf(seats);
    }
}
