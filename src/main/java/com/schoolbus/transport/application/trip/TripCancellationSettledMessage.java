package com.schoolbus.transport.application.trip;

import java.time.Instant;
import java.util.Objects;

public record TripCancellationSettledMessage(
        long tripId,
        Instant settledAt
) {
    public TripCancellationSettledMessage {
        if (tripId <= 0L) {
            throw new IllegalArgumentException("tripId must be positive");
        }
        settledAt = Objects.requireNonNull(
                settledAt,
                "settledAt must not be null"
        );
    }
}
