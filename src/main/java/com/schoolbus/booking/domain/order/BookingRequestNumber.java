package com.schoolbus.booking.domain.order;

import java.util.Objects;

public record BookingRequestNumber(String value) {

    public BookingRequestNumber {
        String validatedValue = Objects.requireNonNull(
                value,
                "bookingRequestNumber must not be null"
        ).strip();
        if (validatedValue.isEmpty() || validatedValue.length() > 64) {
            throw new IllegalArgumentException(
                    "bookingRequestNumber length must be between 1 and 64"
            );
        }
        value = validatedValue;
    }

    public static BookingRequestNumber of(String value) {
        return new BookingRequestNumber(value);
    }
}
