package com.schoolbus.booking.domain.inventory;

import com.schoolbus.booking.domain.trip.TripReference;

public final class NoSeatAvailableException
        extends RuntimeException {

    public NoSeatAvailableException(TripReference tripReference) {
        super(
                "no seat available for trip: "
                        + tripReference.value()
        );
    }
}
