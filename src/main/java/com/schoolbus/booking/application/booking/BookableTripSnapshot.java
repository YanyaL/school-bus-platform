package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.trip.PublicTripNumber;
import com.schoolbus.booking.domain.trip.TripReference;

import java.time.Instant;
import java.util.Objects;

public record BookableTripSnapshot(
        TripReference tripReference,
        PublicTripNumber tripNumber,
        BookingAmount price,
        Instant departureTime,
        Instant bookingDeadline,
        boolean openForBooking
) {

    public BookableTripSnapshot {
        Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
        );
        Objects.requireNonNull(
                tripNumber,
                "tripNumber must not be null"
        );
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(
                departureTime,
                "departureTime must not be null"
        );
        Objects.requireNonNull(
                bookingDeadline,
                "bookingDeadline must not be null"
        );
        if (!bookingDeadline.isBefore(departureTime)) {
            throw new IllegalArgumentException(
                    "bookingDeadline must be before departureTime"
            );
        }
    }

    public boolean canBookAt(Instant instant) {
        Instant checkedInstant = Objects.requireNonNull(
                instant,
                "instant must not be null"
        );
        return openForBooking
                && checkedInstant.isBefore(bookingDeadline)
                && checkedInstant.isBefore(departureTime);
    }
}
