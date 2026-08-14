package com.schoolbus.transport.domain.trip;

import java.time.Instant;
import java.util.List;

public interface TripSeatRepository {

    void initializeSeats(
            TripId tripId,
            List<String> seatNumbers,
            Instant initializedAt
    );

    List<TripSeatStatus> findSeatStatusesByTripId(TripId tripId);
}
