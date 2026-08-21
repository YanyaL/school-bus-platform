package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.trip.PublicTripNumber;
import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;

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
