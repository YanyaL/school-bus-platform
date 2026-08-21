package com.schoolbus.bookingservice.application.tripcancellation;

import java.util.Objects;

public record TripCancellationRequestedEnvelope(
        String eventId,
        TripCancellationRequestedMessage payload
) {
    public TripCancellationRequestedEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        eventId = eventId.strip();
        payload = Objects.requireNonNull(payload, "payload must not be null");
    }
}
