package com.schoolbus.transport.application.vehicle;

public record UpdateVehicleStatusCommand(
        long vehicleId,
        String status,
        long expectedVersion
) {
}
