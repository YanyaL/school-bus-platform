package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.trip.PublicTripNumber;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class TripNotBookableException
        extends BusinessException {

    public TripNotBookableException(
            PublicTripNumber tripNumber
    ) {
        super(
                ErrorCode.TRIP_NOT_BOOKABLE,
                "trip is not bookable: " + tripNumber
        );
    }
}
