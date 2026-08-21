package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;
import com.schoolbus.bookingservice.shared.domain.identity.UserId;

public final class BookingAlreadyExistsException
        extends BusinessException {

    public BookingAlreadyExistsException(
            UserId userId,
            TripReference tripReference
    ) {
        super(
                ErrorCode.BOOKING_ALREADY_EXISTS,
                "user " + userId.value()
                        + " already has an active booking for trip "
                        + tripReference.value()
        );
    }
}
