package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.trip.PublicTripNumber;
import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;

public final class TripInventoryNotReadyException extends BusinessException {

    public TripInventoryNotReadyException(PublicTripNumber tripNumber) {
        super(
                ErrorCode.TRIP_INVENTORY_NOT_READY,
                "trip inventory is not ready: " + tripNumber
        );
    }
}
