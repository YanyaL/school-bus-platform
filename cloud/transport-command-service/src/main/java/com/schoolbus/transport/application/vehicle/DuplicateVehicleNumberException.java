package com.schoolbus.transport.application.vehicle;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class DuplicateVehicleNumberException extends BusinessException {

    public DuplicateVehicleNumberException(String vehicleNumber) {
        super(
                ErrorCode.VEHICLE_NUMBER_ALREADY_EXISTS,
                "vehicle number already exists: " + vehicleNumber
        );
    }
}
