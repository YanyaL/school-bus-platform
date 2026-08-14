package com.schoolbus.transport.application.trip;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.TripStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminTripView(
        long tripId,
        String tripNumber,
        long vehicleId,
        long routeId,
        Instant departureTime,
        Instant bookingDeadline,
        BigDecimal price,
        TripStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminTripView from(BusTrip trip) {
        return new AdminTripView(
                trip.tripId().value(),
                trip.tripNumber().toString(),
                trip.vehicleId().value(),
                trip.routeId().value(),
                trip.departureTime(),
                trip.bookingDeadline(),
                trip.price().amount(),
                trip.status(),
                trip.version(),
                trip.createdAt(),
                trip.updatedAt()
        );
    }
}
