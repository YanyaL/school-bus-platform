package com.schoolbus.paymentservice.application.refund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PaymentRefundRequiredMessage(
        String paymentNumber,
        String bookingNumber,
        BigDecimal amount,
        String reason,
        Instant paidAt,
        Instant occurredAt
) {

    public PaymentRefundRequiredMessage {
        paymentNumber = requireText(paymentNumber, "paymentNumber");
        bookingNumber = requireText(bookingNumber, "bookingNumber");
        amount = Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        reason = requireText(reason, "reason");
        paidAt = Objects.requireNonNull(paidAt, "paidAt must not be null");
        occurredAt = Objects.requireNonNull(
                occurredAt,
                "occurredAt must not be null"
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
