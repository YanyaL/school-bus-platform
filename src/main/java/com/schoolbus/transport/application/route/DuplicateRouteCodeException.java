package com.schoolbus.transport.application.route;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class DuplicateRouteCodeException extends BusinessException {

    public DuplicateRouteCodeException(String routeCode) {
        super(
                ErrorCode.ROUTE_CODE_ALREADY_EXISTS,
                "route code already exists: " + routeCode
        );
    }
}
