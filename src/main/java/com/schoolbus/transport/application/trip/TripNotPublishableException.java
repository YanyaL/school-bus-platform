package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class TripNotPublishableException extends BusinessException {

    public TripNotPublishableException(long tripId, String reason) {
        super(
                ErrorCode.TRIP_NOT_PUBLISHABLE,
                "trip " + tripId + " cannot be published: " + reason
        );
    }
}
