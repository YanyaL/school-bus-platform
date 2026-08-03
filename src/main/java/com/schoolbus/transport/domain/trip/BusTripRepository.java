package com.schoolbus.transport.domain.trip;

import java.util.Optional;

public interface BusTripRepository {

    BusTrip save(BusTrip trip);

    Optional<BusTrip> findById(TripId tripId);
}
