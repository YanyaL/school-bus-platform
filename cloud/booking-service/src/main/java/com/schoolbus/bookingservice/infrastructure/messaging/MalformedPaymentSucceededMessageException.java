package com.schoolbus.bookingservice.infrastructure.messaging;

public final class MalformedPaymentSucceededMessageException
        extends RuntimeException {

    public MalformedPaymentSucceededMessageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
