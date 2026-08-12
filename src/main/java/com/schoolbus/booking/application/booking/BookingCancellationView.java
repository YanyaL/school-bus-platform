package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;

import java.time.Instant;
import java.util.Objects;

public record BookingCancellationView(
        String bookingNumber,
        BookingStatus status,
        CancellationReason cancelReason,
        Instant cancelledAt
) {

    public BookingCancellationView {
        Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        Objects.requireNonNull(status, "status must not be null");
    }

    public static BookingCancellationView from(BookingOrder bookingOrder) {
        BookingOrder validatedOrder = Objects.requireNonNull(
                bookingOrder,
                "bookingOrder must not be null"
        );
        return new BookingCancellationView(
                validatedOrder.bookingNumber().toString(),
                validatedOrder.status(),
                validatedOrder.cancellationReason(),
                validatedOrder.cancelledAt()
        );
    }
}
