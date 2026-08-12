package com.schoolbus.transport.application.trip.admin;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.TripStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record TripAdminView(
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

    public static TripAdminView from(BusTrip trip) {
        BusTrip validatedTrip = Objects.requireNonNull(
                trip,
                "trip must not be null"
        );
        return new TripAdminView(
                validatedTrip.tripId().value(),
                validatedTrip.tripNumber().toString(),
                validatedTrip.vehicleId().value(),
                validatedTrip.routeId().value(),
                validatedTrip.departureTime(),
                validatedTrip.bookingDeadline(),
                validatedTrip.price().amount(),
                validatedTrip.status(),
                validatedTrip.version(),
                validatedTrip.createdAt(),
                validatedTrip.updatedAt()
        );
    }
}
