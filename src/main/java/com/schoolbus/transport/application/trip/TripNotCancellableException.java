package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.domain.trip.TripStatus;

public final class TripNotCancellableException
        extends BusinessException {

    public TripNotCancellableException(
            long tripId,
            TripStatus status
    ) {
        super(
                ErrorCode.TRIP_NOT_CANCELLABLE,
                "trip " + tripId + " cannot be cancelled in status "
                        + status
        );
    }
}
