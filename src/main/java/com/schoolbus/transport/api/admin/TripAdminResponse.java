package com.schoolbus.transport.api.admin;

import com.schoolbus.transport.application.trip.admin.TripAdminView;
import com.schoolbus.transport.domain.trip.TripStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record TripAdminResponse(
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

    public static TripAdminResponse from(TripAdminView view) {
        TripAdminView validatedView = Objects.requireNonNull(
                view,
                "view must not be null"
        );
        return new TripAdminResponse(
                validatedView.tripId(),
                validatedView.tripNumber(),
                validatedView.vehicleId(),
                validatedView.routeId(),
                validatedView.departureTime(),
                validatedView.bookingDeadline(),
                validatedView.price(),
                validatedView.status(),
                validatedView.version(),
                validatedView.createdAt(),
                validatedView.updatedAt()
        );
    }
}
