package com.schoolbus.booking.api;

import com.schoolbus.booking.application.booking.CreateBookingResult;
import com.schoolbus.booking.domain.order.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record CreateBookingResponse(
        long bookingId,
        String bookingNumber,
        long tripId,
        String seatNumber,
        BigDecimal amount,
        BookingStatus status,
        Instant expiresAt
) {

    public CreateBookingResponse {
        Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public static CreateBookingResponse from(
            CreateBookingResult result
    ) {
        CreateBookingResult validatedResult = Objects.requireNonNull(
                result,
                "result must not be null"
        );
        return new CreateBookingResponse(
                validatedResult.bookingId(),
                validatedResult.bookingNumber(),
                validatedResult.tripId(),
                validatedResult.seatNumber(),
                validatedResult.amount(),
                validatedResult.status(),
                validatedResult.expiresAt()
        );
    }
}
