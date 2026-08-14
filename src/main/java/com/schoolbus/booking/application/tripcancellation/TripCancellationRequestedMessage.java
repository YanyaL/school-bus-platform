package com.schoolbus.booking.application.tripcancellation;

import java.time.Instant;
import java.util.Objects;

public record TripCancellationRequestedMessage(
        long tripId,
        long tripVersion,
        Instant requestedAt
) {
    public TripCancellationRequestedMessage {
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
