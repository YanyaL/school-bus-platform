package com.schoolbus.transport.domain.route;

import java.util.Objects;

public record RouteCode(String value) {

    private static final int MAX_LENGTH = 32;

    public RouteCode {
        Objects.requireNonNull(value, "routeCode must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "routeCode must not be blank"
            );
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "routeCode must not exceed "
                            + MAX_LENGTH
                            + " characters"
            );
        }
        value = normalized;
    }

    public static RouteCode of(String value) {
        return new RouteCode(value);
    }
}
