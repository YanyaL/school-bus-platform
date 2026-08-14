package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class VehicleScheduleConflictException
        extends BusinessException {

    public VehicleScheduleConflictException(long vehicleId) {
        super(
                ErrorCode.VEHICLE_SCHEDULE_CONFLICT,
                "vehicle " + vehicleId
                        + " already has an overlapping trip"
        );
    }
}
