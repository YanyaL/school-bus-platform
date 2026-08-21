package com.schoolbus.bookingservice.infrastructure.messaging;

public class MalformedBookingExpirationMessageException
        extends RuntimeException {

    public MalformedBookingExpirationMessageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
