package com.schoolbus.transport.api.trip;

import com.schoolbus.transport.application.trip.TripSeatMapView;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TripSeatMapResponse(
        long tripId,
        Instant bookingDeadline,
        List<TripSeatResponse> seats
) {

    public TripSeatMapResponse {
        Objects.requireNonNull(
                bookingDeadline,
                "bookingDeadline must not be null"
        );
        seats = seats == null ? List.of() : List.copyOf(seats);
    }

    public static TripSeatMapResponse from(TripSeatMapView view) {
        TripSeatMapView validatedView = Objects.requireNonNull(
                view,
                "view must not be null"
        );
        return new TripSeatMapResponse(
                validatedView.tripId(),
                validatedView.bookingDeadline(),
                validatedView.seats()
                        .stream()
                        .map(TripSeatResponse::from)
                        .toList()
        );
    }
}
