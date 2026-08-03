package com.schoolbus.transport.domain.trip;

public record TripId(long value) {

    public TripId {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "tripId must be positive"
            );
        }
    }

    public static TripId of(long value) {
        return new TripId(value);
    }
}
