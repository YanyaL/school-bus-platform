package com.schoolbus.transport.application.route;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class RouteStatusConflictException extends BusinessException {

    public RouteStatusConflictException(String message) {
        super(ErrorCode.ROUTE_STATUS_CONFLICT, message);
    }
}
