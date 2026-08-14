package com.schoolbus.transport.application.trip;

import java.time.Instant;
import java.util.Objects;

public record TripCancellationRequestedEvent(
        long tripId,
        long tripVersion,
        Instant requestedAt
) {
    public static final String TYPE = "TripCancellationRequested";

    public TripCancellationRequestedEvent {
        if (tripId <= 0L) {
            throw new IllegalArgumentException("tripId must be positive");
        }
        if (tripVersion <= 0L) {
            throw new IllegalArgumentException("tripVersion must be positive");
        }
        requestedAt = Objects.requireNonNull(
                requestedAt,
                "requestedAt must not be null"
        );
    }
}
