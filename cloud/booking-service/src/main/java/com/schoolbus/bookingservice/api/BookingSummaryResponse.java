package com.schoolbus.bookingservice.api;

import com.schoolbus.bookingservice.application.booking.BookingSummaryView;
import com.schoolbus.bookingservice.domain.order.BookingStatus;
import com.schoolbus.bookingservice.shared.api.HttpResourceId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record BookingSummaryResponse(
        String bookingId,
        String bookingNumber,
        String tripNumber,
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
                HttpResourceId.format(validatedView.bookingId()),
                validatedView.bookingNumber(),
                validatedView.tripNumber(),
                validatedView.seatNumber(),
                validatedView.amount(),
                validatedView.status(),
                validatedView.expiresAt(),
                validatedView.createdAt()
        );
    }
}
