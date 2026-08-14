package com.schoolbus.transport.application.trip;

import java.util.Objects;

public record TripCancellationSettledEnvelope(
        String eventId,
        TripCancellationSettledMessage payload
) {
    public TripCancellationSettledEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        eventId = eventId.strip();
        payload = Objects.requireNonNull(payload, "payload must not be null");
    }
}
