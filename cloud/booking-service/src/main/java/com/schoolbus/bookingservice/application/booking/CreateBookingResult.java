package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.domain.order.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record CreateBookingResult(
        long bookingId,
        String bookingNumber,
        long userId,
        String tripNumber,
        String seatNumber,
        BigDecimal amount,
        BookingStatus status,
        Instant expiresAt
) {

    public CreateBookingResult {
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
        Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
        );
    }

    public static CreateBookingResult from(BookingOrder bookingOrder) {
        BookingOrder validatedOrder = Objects.requireNonNull(
                bookingOrder,
                "bookingOrder must not be null"
        );
        return new CreateBookingResult(
                validatedOrder.bookingId().value(),
                validatedOrder.bookingNumber().toString(),
                validatedOrder.userId().value(),
                validatedOrder.tripNumber().toString(),
                validatedOrder.seatNumber().value(),
                validatedOrder.amount().amount(),
                validatedOrder.status(),
                validatedOrder.expiresAt()
        );
    }
}
