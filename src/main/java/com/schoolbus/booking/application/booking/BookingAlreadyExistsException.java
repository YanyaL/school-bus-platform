package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.shared.domain.identity.UserId;

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
