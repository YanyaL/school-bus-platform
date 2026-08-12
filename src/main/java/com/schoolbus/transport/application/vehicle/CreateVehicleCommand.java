package com.schoolbus.transport.application.vehicle;

public record CreateVehicleCommand(
        String licensePlate,
        int seatCount
) {
}
