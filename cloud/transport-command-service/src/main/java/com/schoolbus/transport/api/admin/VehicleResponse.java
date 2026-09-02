package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.HttpResourceId;
import com.schoolbus.transport.application.vehicle.VehicleView;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;

import java.time.Instant;

public record VehicleResponse(
        String vehicleId,
        String vehicleNumber,
        String licensePlate,
        int seatCount,
        VehicleStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static VehicleResponse from(VehicleView view) {
        return new VehicleResponse(
                HttpResourceId.format(view.vehicleId()),
                view.vehicleNumber(),
                view.licensePlate(),
                view.seatCount(),
                view.status(),
                view.version(),
                view.createdAt(),
                view.updatedAt()
        );
    }
}
