package com.schoolbus.transport.application.vehicle;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class VehicleStatusConflictException extends BusinessException {

    public VehicleStatusConflictException(String message) {
        super(ErrorCode.VEHICLE_STATUS_CONFLICT, message);
    }
}
