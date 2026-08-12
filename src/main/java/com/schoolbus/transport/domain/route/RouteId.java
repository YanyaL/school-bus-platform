package com.schoolbus.transport.domain.route;

public record RouteId(long value) {

    public RouteId {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "routeId must be positive"
            );
        }
    }

    public static RouteId of(long value) {
        return new RouteId(value);
    }
}
