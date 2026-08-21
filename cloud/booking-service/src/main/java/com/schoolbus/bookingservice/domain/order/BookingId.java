package com.schoolbus.bookingservice.domain.order;

public record BookingId(long value) {

    public BookingId {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "bookingId must be positive"
            );
        }
    }

    public static BookingId of(long value) {
        return new BookingId(value);
    }
}
