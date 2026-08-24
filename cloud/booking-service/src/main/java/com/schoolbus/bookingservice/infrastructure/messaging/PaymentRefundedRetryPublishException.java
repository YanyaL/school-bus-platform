package com.schoolbus.bookingservice.infrastructure.messaging;

public class PaymentRefundedRetryPublishException extends RuntimeException {

    public PaymentRefundedRetryPublishException(String message) {
        super(message);
    }

    public PaymentRefundedRetryPublishException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
