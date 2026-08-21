package com.schoolbus.bookingservice.domain.inventory;

import com.schoolbus.bookingservice.domain.trip.TripReference;

public final class SeatInventoryOverflowException
        extends RuntimeException {

    public SeatInventoryOverflowException(
            TripReference tripReference
    ) {
        super(
                "seat inventory is already full for trip: "
                        + tripReference.value()
        );
    }
}
