package com.schoolbus.transport.api.admin;

import com.schoolbus.transport.application.trip.AdminTripView;
import com.schoolbus.transport.domain.trip.TripStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminTripResponse(
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

    public static AdminTripResponse from(AdminTripView trip) {
        return new AdminTripResponse(
                trip.tripId(),
                trip.tripNumber(),
                trip.vehicleId(),
                trip.routeId(),
                trip.departureTime(),
                trip.bookingDeadline(),
                trip.price(),
                trip.status(),
                trip.version(),
                trip.createdAt(),
                trip.updatedAt()
        );
    }
}
