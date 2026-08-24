package com.schoolbus.bookingservice.application.payment;

import java.util.Objects;

public record PaymentRefundedEnvelope(
        String eventId,
        PaymentRefundedMessage payload
) {

    public PaymentRefundedEnvelope {
        eventId = requireText(eventId, "eventId");
        Objects.requireNonNull(payload, "payload must not be null");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
