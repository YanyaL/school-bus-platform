package com.schoolbus.transport.application.vehicle;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class VehicleNotFoundException extends BusinessException {

    public VehicleNotFoundException(long vehicleId) {
        super(
                ErrorCode.VEHICLE_NOT_FOUND,
                "vehicle " + vehicleId + " does not exist"
        );
    }
}
