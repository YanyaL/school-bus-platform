package com.schoolbus.transport.domain.trip;

public record VehicleId(long value) {

    public VehicleId {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "vehicleId must be positive"
            );
        }
    }

    public static VehicleId of(long value) {
        return new VehicleId(value);
    }
}
