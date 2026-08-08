package com.schoolbus.booking.application.booking;

public record BookingExpirationResult(
        int scanned,
        int expired,
        int conflicts
) {

    public BookingExpirationResult {
        if (scanned < 0 || expired < 0 || conflicts < 0) {
            throw new IllegalArgumentException(
                    "expiration counts must not be negative"
            );
        }
        if (expired + conflicts > scanned) {
            throw new IllegalArgumentException(
                    "processed counts must not exceed scanned count"
            );
        }
    }
}
