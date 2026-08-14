package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class TripNotFoundException extends BusinessException {

    public TripNotFoundException(long tripId) {
        super(
                ErrorCode.TRIP_NOT_FOUND,
                "trip " + tripId + " does not exist"
        );
    }
}
