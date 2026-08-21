package com.schoolbus.bookingservice.application.tripcancellation;

import java.time.Instant;
import java.util.Objects;

public record TripCancellationBookingsSettledEvent(
        long tripId,
        Instant settledAt
) {
    public static final String TYPE = "TripCancellationBookingsSettled";

    public TripCancellationBookingsSettledEvent {
        if (tripId <= 0L) {
            throw new IllegalArgumentException("tripId must be positive");
        }
        settledAt = Objects.requireNonNull(
                settledAt,
                "settledAt must not be null"
        );
    }
}
