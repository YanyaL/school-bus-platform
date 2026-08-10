package com.schoolbus.payment.application.refund;

import java.math.BigDecimal;
import java.util.Objects;

public record RefundRequest(
        String idempotencyKey,
        String paymentNumber,
        BigDecimal amount,
        String reason
) {

    public RefundRequest {
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        paymentNumber = requireText(paymentNumber, "paymentNumber");
        amount = Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        reason = requireText(reason, "reason");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
