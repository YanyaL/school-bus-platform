package com.schoolbus.booking.api;

import com.schoolbus.booking.application.booking.BookingDetailView;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record BookingDetailResponse(
        long bookingId,
        String bookingNumber,
        long tripId,
        String seatNumber,
        BigDecimal amount,
        BookingStatus status,
        Instant expiresAt,
        Instant paidAt,
        Instant cancelledAt,
        CancellationReason cancelReason,
        Instant createdAt,
        Instant updatedAt
) {

    public static BookingDetailResponse from(BookingDetailView view) {
        BookingDetailView validatedView = Objects.requireNonNull(
                view,
                "view must not be null"
        );
        return new BookingDetailResponse(
                validatedView.bookingId(),
                validatedView.bookingNumber(),
                validatedView.tripId(),
                validatedView.seatNumber(),
                validatedView.amount(),
                validatedView.status(),
                validatedView.expiresAt(),
                validatedView.paidAt(),
                validatedView.cancelledAt(),
                validatedView.cancelReason(),
                validatedView.createdAt(),
                validatedView.updatedAt()
        );
    }
}
