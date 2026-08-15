package com.schoolbus.transportquery.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Stable Redis List cache payload shared with school-bus-core.
 * Includes internal tripId for cache compatibility; HTTP responses omit it.
 */
public record BookableTripView(
        long tripId,
        String tripNumber,
        long vehicleId,
        long routeId,
        Instant departureTime,
        Instant bookingDeadline,
        BigDecimal price
) {
}
