package com.schoolbus.transportquery.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TripQueryRepository {

    List<BookableTripView> findBookableTrips(Instant now, int limit);

    Optional<TripRecord> findByTripNumber(String tripNumber);

    List<TripSeatStatusView> findSeatStatusesByTripId(long tripId);
}
