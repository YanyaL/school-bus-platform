package com.schoolbus.bookingservice.infrastructure.messaging;

public class MalformedPaymentRefundedMessageException
        extends RuntimeException {

    public MalformedPaymentRefundedMessageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
