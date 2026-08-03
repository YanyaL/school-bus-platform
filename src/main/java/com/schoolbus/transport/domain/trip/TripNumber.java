package com.schoolbus.transport.domain.trip;

import java.util.Objects;
import java.util.UUID;

public record TripNumber(UUID value) {

    public TripNumber {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static TripNumber generate() {
        return new TripNumber(UUID.randomUUID());
    }

    public static TripNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "tripNumber must not be blank"
            );
        }
        return new TripNumber(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
