package com.schoolbus.bookingservice.domain.order;

import java.util.Objects;
import java.util.UUID;

public record PaymentReference(UUID value) {

    public PaymentReference {
        Objects.requireNonNull(value, "paymentReference must not be null");
    }

    public static PaymentReference of(String value) {
        return new PaymentReference(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
