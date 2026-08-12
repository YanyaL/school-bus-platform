package com.schoolbus.transport.api.trip;

import com.schoolbus.transport.application.trip.TripSeatMapView;
import com.schoolbus.transport.application.trip.TripSeatStatusView;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TripSeatResponse(
        String seatNumber,
        String status
) {

    public TripSeatResponse {
        Objects.requireNonNull(seatNumber, "seatNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static TripSeatResponse from(TripSeatStatusView view) {
        TripSeatStatusView validatedView = Objects.requireNonNull(
                view,
                "view must not be null"
        );
        return new TripSeatResponse(
                validatedView.seatNumber(),
                validatedView.status()
        );
    }
}
