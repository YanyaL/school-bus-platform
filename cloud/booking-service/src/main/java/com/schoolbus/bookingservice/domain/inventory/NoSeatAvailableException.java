package com.schoolbus.bookingservice.domain.inventory;

import com.schoolbus.bookingservice.domain.trip.TripReference;

public final class NoSeatAvailableException
        extends RuntimeException {

    public NoSeatAvailableException(TripReference tripReference) {
        super(
                "no seat available for trip: "
                        + tripReference.value()
        );
    }
}
