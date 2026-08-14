package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class InvalidTripScheduleException extends BusinessException {

    public InvalidTripScheduleException(String message) {
        super(ErrorCode.INVALID_TRIP_SCHEDULE, message);
    }
}
