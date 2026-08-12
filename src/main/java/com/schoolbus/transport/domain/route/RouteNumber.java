package com.schoolbus.transport.domain.route;

import java.util.Objects;
import java.util.UUID;

public record RouteNumber(String value) {

    private static final int UUID_LENGTH = 36;

    public RouteNumber {
        Objects.requireNonNull(value, "routeNumber must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "routeNumber must not be blank"
            );
        }
        if (normalized.length() != UUID_LENGTH) {
            throw new IllegalArgumentException(
                    "routeNumber must be a UUID string"
            );
        }
        value = normalized;
    }

    public static RouteNumber of(String value) {
        return new RouteNumber(value);
    }

    public static RouteNumber generate() {
        return new RouteNumber(UUID.randomUUID().toString());
    }
}
