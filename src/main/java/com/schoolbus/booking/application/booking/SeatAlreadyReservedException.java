package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class SeatAlreadyReservedException
        extends BusinessException {

    public SeatAlreadyReservedException(
            TripReference tripReference,
            SeatNumber seatNumber
    ) {
        super(
                ErrorCode.SEAT_ALREADY_RESERVED,
                "seat " + seatNumber.value()
                        + " is not available for trip "
                        + tripReference.value()
        );
    }
}
