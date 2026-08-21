package com.schoolbus.paymentservice.domain;

public record PaymentId(long value) {

    public PaymentId {
        if (value <= 0L) {
            throw new IllegalArgumentException("paymentId must be positive");
        }
    }

    public static PaymentId of(long value) {
        return new PaymentId(value);
    }
}
