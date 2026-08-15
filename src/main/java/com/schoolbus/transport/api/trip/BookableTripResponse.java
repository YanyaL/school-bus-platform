package com.schoolbus.transport.api.trip;

import com.schoolbus.shared.api.HttpResourceId;
import com.schoolbus.transport.application.trip.BookableTripView;

import java.math.BigDecimal;
import java.time.Instant;

public record BookableTripResponse(
        String tripNumber,
        String vehicleId,
        String routeId,
        Instant departureTime,
        Instant bookingDeadline,
        BigDecimal price
) {

    public static BookableTripResponse from(
            BookableTripView trip
    ) {
        return new BookableTripResponse(
                trip.tripNumber(),
                HttpResourceId.format(trip.vehicleId()),
                HttpResourceId.format(trip.routeId()),
                trip.departureTime(),
                trip.bookingDeadline(),
                trip.price()
        );
    }
}
