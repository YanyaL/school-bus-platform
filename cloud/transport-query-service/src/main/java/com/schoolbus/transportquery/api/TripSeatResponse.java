package com.schoolbus.transportquery.api;

import com.schoolbus.transportquery.application.TripSeatStatusView;

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
