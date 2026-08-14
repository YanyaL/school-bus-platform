package com.schoolbus.transport.domain.trip;

import com.schoolbus.transport.domain.vehicle.VehicleId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BusTripRepository {

    BusTrip save(BusTrip trip);

    Optional<BusTrip> findById(TripId tripId);

    Optional<BusTrip> findByIdForShare(TripId tripId);

    Optional<BusTrip> findByIdForUpdate(TripId tripId);

    List<BusTrip> findAll(
            TripStatus status,
            int offset,
            int limit
    );

    boolean existsVehicleScheduleConflict(
            VehicleId vehicleId,
            Instant departureTime,
            Instant arrivalTime
    );

    List<BusTrip> findBookableTrips(
            Instant now,
            int limit
    );

    List<BusTrip> findDueOpenTripsForClosing(
            Instant now,
            int limit
    );

    List<BusTrip> findDueClosedTripsForDeparture(
            Instant now,
            int limit
    );
}
