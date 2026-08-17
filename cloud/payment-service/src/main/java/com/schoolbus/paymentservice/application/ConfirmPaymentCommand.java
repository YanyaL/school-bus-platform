package com.schoolbus.paymentservice.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConfirmPaymentCommand(
        String requestNumber,
        String paymentNumber,
        String bookingNumber,
        BigDecimal amount,
        Instant paidAt
) {

    public ConfirmPaymentCommand {
        requestNumber = requireText(requestNumber, "requestNumber", 64);
        paymentNumber = requireUuid(paymentNumber, "paymentNumber");
        bookingNumber = requireUuid(bookingNumber, "bookingNumber");
        amount = Objects.requireNonNull(amount, "amount must not be null")
                .setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        paidAt = Objects.requireNonNull(paidAt, "paidAt must not be null");
    }

    private static String requireText(
            String value,
            String name,
            int maximumLength
    ) {
        String normalized = Objects.requireNonNull(
                value,
                name + " must not be null"
        ).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(
                    name + " length must be between 1 and " + maximumLength
            );
        }
        return normalized;
    }

    private static String requireUuid(String value, String name) {
        try {
            return UUID.fromString(requireText(value, name, 36)).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    name + " must be a valid UUID",
                    exception
            );
        }
    }
}
