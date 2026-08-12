package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingNumber;

import java.util.Objects;

public record CancelMyBookingCommand(
        long userId,
        String bookingNumber
) {

    public CancelMyBookingCommand {
        if (bookingNumber == null || bookingNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "bookingNumber must not be blank"
            );
        }
        bookingNumber = bookingNumber.strip();
        Objects.requireNonNull(bookingNumber, "bookingNumber must not be null");
    }

    public BookingNumber toBookingNumber() {
        return BookingNumber.of(bookingNumber);
    }
}
