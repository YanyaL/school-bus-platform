package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;

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
