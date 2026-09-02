package com.schoolbus.transport.application.route;

public record CreateRouteCommand(
        String routeCode,
        String departureCampus,
        String arrivalCampus,
        int estimatedDurationMinutes
) {
}
