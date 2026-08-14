package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class TripSeatTemplateInvalidException extends BusinessException {

    public TripSeatTemplateInvalidException(long vehicleId) {
        super(
                ErrorCode.TRIP_SEAT_TEMPLATE_INVALID,
                "vehicle " + vehicleId
                        + " has a missing or inconsistent seat template"
        );
    }
}
