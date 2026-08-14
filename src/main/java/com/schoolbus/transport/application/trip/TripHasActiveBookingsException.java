package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class TripHasActiveBookingsException
        extends BusinessException {

    public TripHasActiveBookingsException(long tripId) {
        super(
                ErrorCode.TRIP_HAS_ACTIVE_BOOKINGS,
                "trip " + tripId + " has active bookings"
        );
    }
}
