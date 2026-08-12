package com.schoolbus.booking.api;

import com.schoolbus.booking.application.booking.BookingCancellationView;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;

import java.time.Instant;
import java.util.Objects;

public record BookingCancellationResponse(
        String bookingNumber,
        BookingStatus status,
        CancellationReason cancelReason,
        Instant cancelledAt
) {

    public static BookingCancellationResponse from(
            BookingCancellationView view
    ) {
        BookingCancellationView validatedView = Objects.requireNonNull(
                view,
                "view must not be null"
        );
        return new BookingCancellationResponse(
                validatedView.bookingNumber(),
                validatedView.status(),
                validatedView.cancelReason(),
                validatedView.cancelledAt()
        );
    }
}
