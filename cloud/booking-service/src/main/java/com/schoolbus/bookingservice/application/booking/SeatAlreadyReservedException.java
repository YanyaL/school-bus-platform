package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.SeatNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;

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
