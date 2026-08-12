package com.schoolbus.transport.domain.trip;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BusTripRepository {

    BusTrip save(BusTrip trip);

    Optional<BusTrip> findById(TripId tripId);

    Optional<BusTrip> findByTripNumber(TripNumber tripNumber);

    boolean existsActiveTripForVehicleDeparture(
            VehicleId vehicleId,
            TripId excludingTripId,
            Instant departureTime
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
