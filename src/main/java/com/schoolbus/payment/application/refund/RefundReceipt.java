package com.schoolbus.payment.application.refund;

import java.time.Instant;
import java.util.Objects;

public record RefundReceipt(
        String refundReference,
        Instant refundedAt
) {

    public RefundReceipt {
        if (refundReference == null || refundReference.isBlank()) {
            throw new IllegalArgumentException(
                    "refundReference must not be blank"
            );
        }
        refundReference = refundReference.strip();
        refundedAt = Objects.requireNonNull(
                refundedAt,
                "refundedAt must not be null"
        );
    }
}
