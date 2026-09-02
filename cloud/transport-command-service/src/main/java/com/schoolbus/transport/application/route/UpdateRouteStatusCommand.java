package com.schoolbus.transport.application.route;

public record UpdateRouteStatusCommand(
        long routeId,
        String status,
        long expectedVersion
) {
}
