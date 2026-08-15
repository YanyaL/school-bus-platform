package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record BookingDetailView(
        long bookingId,
        String bookingNumber,
        String tripNumber,
        String seatNumber,
        BigDecimal amount,
        BookingStatus status,
        Instant expiresAt,
        Instant paidAt,
        Instant cancelledAt,
        CancellationReason cancelReason,
        Instant createdAt
) {

    public BookingDetailView {
        Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        Objects.requireNonNull(
                tripNumber,
                "tripNumber must not be null"
        );
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static BookingDetailView from(BookingOrder bookingOrder) {
        BookingOrder validatedOrder = Objects.requireNonNull(
                bookingOrder,
                "bookingOrder must not be null"
        );
        return new BookingDetailView(
                validatedOrder.bookingId().value(),
                validatedOrder.bookingNumber().toString(),
                validatedOrder.tripNumber().toString(),
                validatedOrder.seatNumber().value(),
                validatedOrder.amount().amount(),
                validatedOrder.status(),
                validatedOrder.expiresAt(),
                validatedOrder.paidAt(),
                validatedOrder.cancelledAt(),
                validatedOrder.cancellationReason(),
                validatedOrder.createdAt()
        );
    }
}
