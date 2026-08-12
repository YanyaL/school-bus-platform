package com.schoolbus.transport.domain.trip;

import java.util.List;

public interface TripSeatRepository {

    List<TripSeatStatus> findSeatStatusesByTripId(TripId tripId);
}
