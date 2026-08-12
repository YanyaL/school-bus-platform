package com.schoolbus.transport.domain.vehicle;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository {

    Vehicle save(Vehicle vehicle);

    Optional<Vehicle> findById(VehicleId vehicleId);

    Optional<Vehicle> findByVehicleNumber(VehicleNumber vehicleNumber);

    Optional<Vehicle> findByLicensePlate(LicensePlate licensePlate);

    List<Vehicle> findAll(
            VehicleStatus status,
            int offset,
            int limit
    );

    int count(VehicleStatus status);

    void saveSeatTemplate(
            VehicleId vehicleId,
            SeatLayout seatLayout,
            java.time.Instant createdAt
    );

    List<String> findSeatNumbersByVehicleId(VehicleId vehicleId);
}
