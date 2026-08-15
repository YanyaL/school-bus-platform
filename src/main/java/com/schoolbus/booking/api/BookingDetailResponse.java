package com.schoolbus.booking.api;

import com.schoolbus.booking.application.booking.BookingDetailView;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;
import com.schoolbus.shared.api.HttpResourceId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record BookingDetailResponse(
        String bookingId,
        String bookingNumber,
        String tripId,
        String seatNumber,
        BigDecimal amount,
        BookingStatus status,
        Instant expiresAt,
        Instant paidAt,
        Instant cancelledAt,
        CancellationReason cancelReason,
        Instant createdAt
) {

    public BookingDetailResponse {
        Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static BookingDetailResponse from(BookingDetailView view) {
        BookingDetailView validatedView = Objects.requireNonNull(
                view,
                "view must not be null"
        );
        return new BookingDetailResponse(
                HttpResourceId.format(validatedView.bookingId()),
                validatedView.bookingNumber(),
                HttpResourceId.format(validatedView.tripId()),
                validatedView.seatNumber(),
                validatedView.amount(),
                validatedView.status(),
                validatedView.expiresAt(),
                validatedView.paidAt(),
                validatedView.cancelledAt(),
                validatedView.cancelReason(),
                validatedView.createdAt()
        );
    }
}
