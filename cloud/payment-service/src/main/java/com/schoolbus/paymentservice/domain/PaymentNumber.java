package com.schoolbus.paymentservice.domain;

import java.util.Objects;
import java.util.UUID;

public record PaymentNumber(UUID value) {

    public PaymentNumber {
        Objects.requireNonNull(value, "paymentNumber must not be null");
    }

    public static PaymentNumber of(String value) {
        return new PaymentNumber(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
