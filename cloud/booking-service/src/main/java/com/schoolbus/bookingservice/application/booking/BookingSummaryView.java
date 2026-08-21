package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.domain.order.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record BookingSummaryView(
        long bookingId,
        String bookingNumber,
        String tripNumber,
        String seatNumber,
        BigDecimal amount,
        BookingStatus status,
        Instant expiresAt,
        Instant createdAt
) {

    public BookingSummaryView {
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

    public static BookingSummaryView from(BookingOrder bookingOrder) {
        BookingOrder validatedOrder = Objects.requireNonNull(
                bookingOrder,
                "bookingOrder must not be null"
        );
        return new BookingSummaryView(
                validatedOrder.bookingId().value(),
                validatedOrder.bookingNumber().toString(),
                validatedOrder.tripNumber().toString(),
                validatedOrder.seatNumber().value(),
                validatedOrder.amount().amount(),
                validatedOrder.status(),
                validatedOrder.expiresAt(),
                validatedOrder.createdAt()
        );
    }
}
