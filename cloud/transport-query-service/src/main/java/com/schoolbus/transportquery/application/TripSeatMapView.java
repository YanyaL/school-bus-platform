package com.schoolbus.transportquery.application;

import java.time.Instant;
import java.util.List;

public record TripSeatMapView(
        String tripNumber,
        Instant bookingDeadline,
        List<TripSeatStatusView> seats
) {
}
