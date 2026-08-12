package com.schoolbus.transport.api.admin;

import com.schoolbus.transport.application.route.RouteView;
import com.schoolbus.transport.domain.route.Campus;
import com.schoolbus.transport.domain.route.RouteStatus;

import java.time.Instant;

public record RouteResponse(
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

    public static RouteResponse from(RouteView view) {
        return new RouteResponse(
                view.routeId(),
                view.routeNumber(),
                view.routeCode(),
                view.departureCampus(),
                view.arrivalCampus(),
                view.estimatedDurationMinutes(),
                view.status(),
                view.version(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
