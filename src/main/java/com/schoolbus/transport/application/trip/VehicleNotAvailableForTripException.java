package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class VehicleNotAvailableForTripException
        extends BusinessException {

    public VehicleNotAvailableForTripException(long vehicleId) {
        super(
                ErrorCode.VEHICLE_NOT_AVAILABLE_FOR_TRIP,
                "vehicle " + vehicleId + " is disabled"
        );
    }
}
