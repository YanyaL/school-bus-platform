package com.schoolbus.bookingservice.domain.trip;

public record TripReference(long value) {

    public TripReference {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "tripReference must be positive"
            );
        }
    }

    public static TripReference of(long value) {
        return new TripReference(value);
    }
}
