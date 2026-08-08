package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;

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
