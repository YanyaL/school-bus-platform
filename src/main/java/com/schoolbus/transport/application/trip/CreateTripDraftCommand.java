package com.schoolbus.transport.application.trip;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateTripDraftCommand(
        long vehicleId,
        long routeId,
        Instant departureTime,
        Instant bookingDeadline,
        BigDecimal price
) {
}
