package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;

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
