package com.schoolbus.booking.domain.order;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record SeatNumber(String value) {

    private static final Pattern FORMAT = Pattern.compile(
            "^[A-Z0-9-]{1,10}$"
    );

    public SeatNumber {
        String validatedValue = Objects.requireNonNull(
                value,
                "seatNumber must not be null"
        ).strip().toUpperCase(Locale.ROOT);
        if (!FORMAT.matcher(validatedValue).matches()) {
            throw new IllegalArgumentException(
                    "seatNumber must contain 1 to 10 letters, digits or hyphens"
            );
        }
        value = validatedValue;
    }

    public static SeatNumber of(String value) {
        return new SeatNumber(value);
    }
}
