package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;

import java.time.Instant;
import java.util.Objects;

public record BookingPaymentDeadlineEvent(
        BookingId bookingId,
        BookingNumber bookingNumber,
        Instant expiresAt,
        Instant occurredAt,
        long aggregateVersion
) {

    public static final String TYPE = "BookingPaymentDeadlineReached";

    public BookingPaymentDeadlineEvent {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
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
        if (aggregateVersion < 0) {
            throw new IllegalArgumentException(
                    "aggregateVersion must not be negative"
            );
        }
    }
}
