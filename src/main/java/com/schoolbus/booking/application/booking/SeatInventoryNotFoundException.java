package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class SeatInventoryNotFoundException
        extends BusinessException {

    public SeatInventoryNotFoundException(
            TripReference tripReference
    ) {
        super(
                ErrorCode.SEAT_INVENTORY_NOT_FOUND,
                "seat inventory does not exist for trip: "
                        + tripReference.value()
        );
    }
}
