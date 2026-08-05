package com.schoolbus.transport.api.trip;

import com.schoolbus.transport.application.trip.BookableTripView;

import java.math.BigDecimal;
import java.time.Instant;

public record BookableTripResponse(
        long tripId,
        String tripNumber,
        long vehicleId,
        long routeId,
        Instant departureTime,
        Instant bookingDeadline,
        BigDecimal price
) {

    public static BookableTripResponse from(
            BookableTripView trip
    ) {
        return new BookableTripResponse(
                trip.tripId(),
                trip.tripNumber(),
                trip.vehicleId(),
                trip.routeId(),
                trip.departureTime(),
                trip.bookingDeadline(),
                trip.price()
        );
    }
}
