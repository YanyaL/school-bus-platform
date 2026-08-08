package com.schoolbus.booking.domain.inventory;

import com.schoolbus.booking.domain.trip.TripReference;

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
