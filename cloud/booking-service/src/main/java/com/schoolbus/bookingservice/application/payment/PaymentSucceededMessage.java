package com.schoolbus.bookingservice.application.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PaymentSucceededMessage(
        int schemaVersion,
        String paymentNumber,
        String bookingNumber,
        BigDecimal amount,
        Instant paidAt,
        Instant occurredAt
) {

    public PaymentSucceededMessage {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException(
                    "unsupported PaymentSucceeded schema version"
            );
        }
        paymentNumber = requireText(paymentNumber, "paymentNumber");
        bookingNumber = requireText(bookingNumber, "bookingNumber");
        UUID.fromString(paymentNumber);
        UUID.fromString(bookingNumber);
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Objects.requireNonNull(paidAt, "paidAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (occurredAt.isBefore(paidAt)) {
            throw new IllegalArgumentException(
                    "occurredAt must not be before paidAt"
            );
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
