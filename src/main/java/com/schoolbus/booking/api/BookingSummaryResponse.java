package com.schoolbus.booking.api;

import com.schoolbus.booking.application.booking.BookingSummaryView;
import com.schoolbus.booking.domain.order.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record BookingSummaryResponse(
        long bookingId,
        String bookingNumber,
        long tripId,
        String seatNumber,
        BigDecimal amount,
        BookingStatus status,
        Instant expiresAt,
        Instant createdAt
) {

    public BookingSummaryResponse {
        Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static BookingSummaryResponse from(
            BookingSummaryView view
    ) {
        BookingSummaryView validatedView = Objects.requireNonNull(
                view,
                "view must not be null"
        );
        return new BookingSummaryResponse(
                validatedView.bookingId(),
                validatedView.bookingNumber(),
                validatedView.tripId(),
                validatedView.seatNumber(),
                validatedView.amount(),
                validatedView.status(),
                validatedView.expiresAt(),
                validatedView.createdAt()
        );
    }
}
