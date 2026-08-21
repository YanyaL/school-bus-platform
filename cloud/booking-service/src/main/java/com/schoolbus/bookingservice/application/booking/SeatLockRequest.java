package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.SeatNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.domain.identity.UserId;

import java.time.Instant;
import java.util.Objects;

public record SeatLockRequest(
        TripReference tripReference,
        SeatNumber seatNumber,
        BookingNumber bookingNumber,
        UserId userId,
        Instant lockExpiresAt,
        Instant lockedAt
) {

    public SeatLockRequest {
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
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(
                lockExpiresAt,
                "lockExpiresAt must not be null"
        );
        Objects.requireNonNull(
                lockedAt,
                "lockedAt must not be null"
        );
        if (!lockExpiresAt.isAfter(lockedAt)) {
            throw new IllegalArgumentException(
                    "lockExpiresAt must be after lockedAt"
            );
        }
    }
}
