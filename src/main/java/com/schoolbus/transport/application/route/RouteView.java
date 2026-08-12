package com.schoolbus.transport.application.route;

import com.schoolbus.transport.domain.route.Campus;
import com.schoolbus.transport.domain.route.RouteStatus;

import java.time.Instant;

public record RouteView(
        long routeId,
        String routeNumber,
        String routeCode,
        Campus departureCampus,
        Campus arrivalCampus,
        int estimatedDurationMinutes,
        RouteStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
