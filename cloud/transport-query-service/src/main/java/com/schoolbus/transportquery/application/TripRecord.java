package com.schoolbus.transportquery.application;

import java.time.Instant;

public record TripRecord(
        long tripId,
        String tripNumber,
        Instant bookingDeadline
) {
}
