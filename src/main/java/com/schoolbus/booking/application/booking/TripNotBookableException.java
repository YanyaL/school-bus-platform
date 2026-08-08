package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class TripNotBookableException
        extends BusinessException {

    public TripNotBookableException(
            TripReference tripReference
    ) {
        super(
                ErrorCode.TRIP_NOT_BOOKABLE,
                "trip is not bookable: " + tripReference.value()
        );
    }
}
