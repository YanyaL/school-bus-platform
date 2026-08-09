package com.schoolbus.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record ConfirmPaymentCommand(
        String requestNumber,
        String paymentNumber,
        String bookingNumber,
        BigDecimal amount,
        Instant paidAt
) {
    public ConfirmPaymentCommand {
        requestNumber = requireText(requestNumber, "requestNumber");
        paymentNumber = requireText(paymentNumber, "paymentNumber");
        bookingNumber = requireText(bookingNumber, "bookingNumber");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(paidAt, "paidAt must not be null");
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " must not be null").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
