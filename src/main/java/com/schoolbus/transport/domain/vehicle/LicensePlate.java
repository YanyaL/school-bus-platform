package com.schoolbus.transport.domain.vehicle;

import java.util.Objects;

public record LicensePlate(String value) {

    private static final int MAX_LENGTH = 20;

    public LicensePlate {
        Objects.requireNonNull(value, "licensePlate must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "licensePlate must not be blank"
            );
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "licensePlate must not exceed "
                            + MAX_LENGTH
                            + " characters"
            );
        }
        value = normalized;
    }

    public static LicensePlate of(String value) {
        return new LicensePlate(value);
    }
}
