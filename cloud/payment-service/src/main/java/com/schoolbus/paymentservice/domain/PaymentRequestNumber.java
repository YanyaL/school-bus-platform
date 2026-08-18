package com.schoolbus.paymentservice.domain;

import java.util.Objects;

public record PaymentRequestNumber(String value) {

    public PaymentRequestNumber {
        String validated = Objects.requireNonNull(
                value,
                "paymentRequestNumber must not be null"
        ).strip();
        if (validated.isEmpty() || validated.length() > 64) {
            throw new IllegalArgumentException(
                    "paymentRequestNumber length must be between 1 and 64"
            );
        }
        value = validated;
    }

    public static PaymentRequestNumber of(String value) {
        return new PaymentRequestNumber(value);
    }
}
