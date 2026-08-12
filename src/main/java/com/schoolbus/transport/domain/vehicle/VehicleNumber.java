package com.schoolbus.transport.domain.vehicle;

import java.util.Objects;
import java.util.UUID;

public record VehicleNumber(String value) {

    private static final int UUID_LENGTH = 36;

    public VehicleNumber {
        Objects.requireNonNull(value, "vehicleNumber must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "vehicleNumber must not be blank"
            );
        }
        if (normalized.length() != UUID_LENGTH) {
            throw new IllegalArgumentException(
                    "vehicleNumber must be a UUID string"
            );
        }
        value = normalized;
    }

    public static VehicleNumber of(String value) {
        return new VehicleNumber(value);
    }

    public static VehicleNumber generate() {
        return new VehicleNumber(UUID.randomUUID().toString());
    }
}
