package com.schoolbus.bookingservice.domain.order;

import java.util.Objects;
import java.util.UUID;

public record BookingNumber(UUID value) {

    public BookingNumber {
        Objects.requireNonNull(value, "bookingNumber must not be null");
    }

    public static BookingNumber of(String value) {
        return new BookingNumber(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
