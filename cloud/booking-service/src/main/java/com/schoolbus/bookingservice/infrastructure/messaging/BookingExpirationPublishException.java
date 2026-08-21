package com.schoolbus.bookingservice.infrastructure.messaging;

public class BookingExpirationPublishException extends RuntimeException {

    public BookingExpirationPublishException(String message) {
        super(message);
    }

    public BookingExpirationPublishException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
