package com.schoolbus.transportquery.api;

import com.schoolbus.transportquery.application.TripSeatMapView;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TripSeatMapResponse(
        String tripNumber,
        Instant bookingDeadline,
        List<TripSeatResponse> seats
) {

    public TripSeatMapResponse {
        Objects.requireNonNull(tripNumber, "tripNumber must not be null");
        Objects.requireNonNull(bookingDeadline, "bookingDeadline must not be null");
        seats = seats == null ? List.of() : List.copyOf(seats);
    }

    public static TripSeatMapResponse from(TripSeatMapView view) {
        TripSeatMapView validatedView = Objects.requireNonNull(view, "view must not be null");
        return new TripSeatMapResponse(
                validatedView.tripNumber(),
                validatedView.bookingDeadline(),
                validatedView.seats()
                        .stream()
                        .map(TripSeatResponse::from)
                        .toList()
        );
    }
}
