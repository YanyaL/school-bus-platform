package com.schoolbus.transport.domain.route;

import java.util.List;
import java.util.Optional;

public interface RouteRepository {

    Route save(Route route);

    Optional<Route> findById(RouteId routeId);

    Optional<Route> findByRouteCode(RouteCode routeCode);

    List<Route> findAll(
            RouteStatus status,
            int offset,
            int limit
    );

    int count(RouteStatus status);
}
