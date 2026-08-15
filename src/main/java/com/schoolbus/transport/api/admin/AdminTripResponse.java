package com.schoolbus.transport.api.admin;

import com.schoolbus.shared.api.HttpResourceId;
import com.schoolbus.transport.application.trip.AdminTripView;
import com.schoolbus.transport.domain.trip.TripStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminTripResponse(
        String tripId,
        String tripNumber,
        String vehicleId,
        String routeId,
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
                HttpResourceId.format(trip.tripId()),
                trip.tripNumber(),
                HttpResourceId.format(trip.vehicleId()),
                HttpResourceId.format(trip.routeId()),
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
