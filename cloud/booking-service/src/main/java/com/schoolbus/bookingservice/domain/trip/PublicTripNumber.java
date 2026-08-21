package com.schoolbus.bookingservice.domain.trip;

import java.util.Objects;
import java.util.UUID;

/**
 * Booking-owned public trip identifier (UUID string).
 * Must not import Transport's TripNumber type.
 */
public record PublicTripNumber(UUID value) {

    public PublicTripNumber {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PublicTripNumber of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "tripNumber must not be blank"
            );
        }
        try {
            return new PublicTripNumber(UUID.fromString(value.strip()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "tripNumber must be a valid UUID",
                    exception
            );
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
