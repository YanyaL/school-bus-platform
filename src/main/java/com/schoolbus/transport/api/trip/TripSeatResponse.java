package com.schoolbus.transport.api.trip;

import com.schoolbus.transport.application.trip.TripSeatView;

import java.util.Objects;

public record TripSeatResponse(
        String seatNumber,
        String status
) {

    public static TripSeatResponse from(TripSeatView view) {
        TripSeatView validatedView = Objects.requireNonNull(
                view,
                "view must not be null"
        );
        return new TripSeatResponse(
                validatedView.seatNumber(),
                validatedView.status()
        );
    }
}
