package com.schoolbus.bookingservice.infrastructure.messaging;

public final class PaymentSucceededRetryPublishException
        extends RuntimeException {

    public PaymentSucceededRetryPublishException(String message) {
        super(message);
    }

    public PaymentSucceededRetryPublishException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
