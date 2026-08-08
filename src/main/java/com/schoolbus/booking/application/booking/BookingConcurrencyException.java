package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class BookingConcurrencyException
        extends BusinessException {

    public BookingConcurrencyException(
            TripReference tripReference
    ) {
        super(
                ErrorCode.BOOKING_CONCURRENCY_CONFLICT,
                "booking could not be completed after concurrent "
                        + "updates for trip: "
                        + tripReference.value()
        );
    }
}
