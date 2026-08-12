package com.schoolbus.transport.application.route;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class RouteNotFoundException extends BusinessException {

    public RouteNotFoundException(long routeId) {
        super(
                ErrorCode.ROUTE_NOT_FOUND,
                "route " + routeId + " does not exist"
        );
    }
}
