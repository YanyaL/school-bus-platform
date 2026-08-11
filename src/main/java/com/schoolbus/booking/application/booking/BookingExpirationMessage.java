package com.schoolbus.booking.application.booking;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BookingExpirationMessage(
        long bookingId,
        UUID bookingNumber,
        Instant expiresAt,
        Instant occurredAt
) {

    public BookingExpirationMessage {
        if (bookingId <= 0) {
            throw new IllegalArgumentException(
                    "bookingId must be positive"
            );
        }
        Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (!expiresAt.isAfter(occurredAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after occurredAt"
            );
        }
    }
}
