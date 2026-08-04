package com.schoolbus.transport.application.trip;

import java.time.Instant;
import java.util.Objects;

public record TripAvailabilityChangedEvent(Instant occurredAt) {

    public TripAvailabilityChangedEvent {
        Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
        );
    }
}
