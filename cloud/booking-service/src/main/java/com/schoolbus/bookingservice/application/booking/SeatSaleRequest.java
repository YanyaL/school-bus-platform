package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.SeatNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;

import java.time.Instant;
import java.util.Objects;

public record SeatSaleRequest(
        TripReference tripReference,
        SeatNumber seatNumber,
        BookingNumber bookingNumber,
        Instant soldAt
) {
    public SeatSaleRequest {
        Objects.requireNonNull(tripReference, "tripReference must not be null");
        Objects.requireNonNull(seatNumber, "seatNumber must not be null");
        Objects.requireNonNull(bookingNumber, "bookingNumber must not be null");
        Objects.requireNonNull(soldAt, "soldAt must not be null");
    }
}
