package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.domain.order.BookingStatus;
import com.schoolbus.bookingservice.domain.order.CancellationReason;

import java.time.Instant;
import java.util.Objects;

public record CancelBookingResult(
        String bookingNumber,
        BookingStatus status,
        CancellationReason cancelReason,
        Instant cancelledAt,
        boolean newlyCancelled
) {

    public CancelBookingResult {
        Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        Objects.requireNonNull(status, "status must not be null");
    }

    public static CancelBookingResult from(
            BookingOrder bookingOrder,
            boolean newlyCancelled
    ) {
        BookingOrder validatedOrder = Objects.requireNonNull(
                bookingOrder,
                "bookingOrder must not be null"
        );
        return new CancelBookingResult(
                validatedOrder.bookingNumber().toString(),
                validatedOrder.status(),
                validatedOrder.cancellationReason(),
                validatedOrder.cancelledAt(),
                newlyCancelled
        );
    }
}
