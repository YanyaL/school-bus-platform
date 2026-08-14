package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class RouteNotAvailableForTripException
        extends BusinessException {

    public RouteNotAvailableForTripException(long routeId) {
        super(
                ErrorCode.ROUTE_NOT_AVAILABLE_FOR_TRIP,
                "route " + routeId + " is disabled"
        );
    }
}
