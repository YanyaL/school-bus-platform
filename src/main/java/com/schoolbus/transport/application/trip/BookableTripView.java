package com.schoolbus.transport.application.trip;

import com.schoolbus.transport.domain.trip.BusTrip;

import java.math.BigDecimal;
import java.time.Instant;

public record BookableTripView(
        long tripId,
        String tripNumber,
        long vehicleId,
        long routeId,
        Instant departureTime,
        Instant bookingDeadline,
        BigDecimal price
) {

    public static BookableTripView from(BusTrip trip) {
        return new BookableTripView(
                trip.tripId().value(),
                trip.tripNumber().toString(),
                trip.vehicleId().value(),
                trip.routeId().value(),
                trip.departureTime(),
                trip.bookingDeadline(),
                trip.price().amount()
        );
    }
}
