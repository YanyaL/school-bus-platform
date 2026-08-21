package com.schoolbus.bookingservice.application.payment;

import java.util.Objects;

public record PaymentSucceededEnvelope(
        String eventId,
        PaymentSucceededMessage payload
) {

    public PaymentSucceededEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        eventId = eventId.strip();
        Objects.requireNonNull(payload, "payload must not be null");
    }
}
