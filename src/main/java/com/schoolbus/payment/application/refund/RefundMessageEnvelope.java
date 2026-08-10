package com.schoolbus.payment.application.refund;

import java.util.Objects;

public record RefundMessageEnvelope(
        String eventId,
        PaymentRefundRequiredMessage payload
) {

    public RefundMessageEnvelope {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        eventId = eventId.strip();
        payload = Objects.requireNonNull(payload, "payload must not be null");
    }
}
