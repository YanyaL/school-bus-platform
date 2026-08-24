package com.schoolbus.paymentservice.application.refund;

import com.schoolbus.paymentservice.domain.BookingNumber;

import java.time.Instant;
import java.util.Objects;

public record PaymentRefundedCommand(
        String paymentNumber,
        BookingNumber bookingNumber,
        String refundReference,
        String reason,
        Instant refundedAt,
        Instant occurredAt
) {

    public PaymentRefundedCommand {
        paymentNumber = requireText(paymentNumber, "paymentNumber");
        Objects.requireNonNull(bookingNumber, "bookingNumber must not be null");
        refundReference = requireText(refundReference, "refundReference");
        reason = requireText(reason, "reason");
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
