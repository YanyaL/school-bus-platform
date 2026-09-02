package com.schoolbus.transport.application.vehicle;

import com.schoolbus.transport.domain.vehicle.VehicleStatus;

import java.time.Instant;

public record VehicleView(
        long vehicleId,
        String vehicleNumber,
        String licensePlate,
        int seatCount,
        VehicleStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
