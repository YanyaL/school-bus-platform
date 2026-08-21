package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.SeatNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;

import java.time.Instant;
import java.util.Objects;

public record SeatReleaseRequest(
        TripReference tripReference,
        SeatNumber seatNumber,
        BookingNumber bookingNumber,
        Instant releasedAt
) {

    public SeatReleaseRequest {
        Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
        );
        Objects.requireNonNull(
                seatNumber,
                "seatNumber must not be null"
        );
        Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        Objects.requireNonNull(
                releasedAt,
                "releasedAt must not be null"
        );
    }
}
