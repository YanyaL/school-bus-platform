package com.schoolbus.transport.application.vehicle;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class DuplicateLicensePlateException extends BusinessException {

    public DuplicateLicensePlateException(String licensePlate) {
        super(
                ErrorCode.LICENSE_PLATE_ALREADY_EXISTS,
                "license plate already exists: " + licensePlate
        );
    }
}
