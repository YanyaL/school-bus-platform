package com.schoolbus.transport.application.trip;

import java.time.Instant;

public interface TripInventoryInitializationPort {

    void initialize(
            long tripId,
            int totalSeats,
            Instant initializedAt
    );
}
