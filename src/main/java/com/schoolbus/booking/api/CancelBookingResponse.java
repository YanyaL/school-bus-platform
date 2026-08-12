package com.schoolbus.booking.api;

import com.schoolbus.booking.application.booking.CancelBookingResult;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;

import java.time.Instant;
import java.util.Objects;

public record CancelBookingResponse(
        String bookingNumber,
        BookingStatus status,
        CancellationReason cancelReason,
        Instant cancelledAt
) {

    public CancelBookingResponse {
        Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        Objects.requireNonNull(status, "status must not be null");
    }

    public static CancelBookingResponse from(CancelBookingResult result) {
        CancelBookingResult validatedResult = Objects.requireNonNull(
                result,
                "result must not be null"
        );
        return new CancelBookingResponse(
                validatedResult.bookingNumber(),
                validatedResult.status(),
                validatedResult.cancelReason(),
                validatedResult.cancelledAt()
        );
    }
}
