package com.schoolbus.bookingservice.application.payment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentRefundedMessage(
        String paymentNumber,
        String bookingNumber,
        String refundReference,
        String reason,
        Instant refundedAt,
        Instant occurredAt
) {

    public PaymentRefundedMessage {
        paymentNumber = requireText(paymentNumber, "paymentNumber");
        bookingNumber = requireText(bookingNumber, "bookingNumber");
        refundReference = requireText(refundReference, "refundReference");
        reason = requireText(reason, "reason");
        UUID.fromString(paymentNumber);
        UUID.fromString(bookingNumber);
        Objects.requireNonNull(refundedAt, "refundedAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
