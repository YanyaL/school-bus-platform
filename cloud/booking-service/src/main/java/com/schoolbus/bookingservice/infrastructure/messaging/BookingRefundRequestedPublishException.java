package com.schoolbus.bookingservice.infrastructure.messaging;

public class BookingRefundRequestedPublishException extends RuntimeException {

    public BookingRefundRequestedPublishException(String message) {
        super(message);
    }

    public BookingRefundRequestedPublishException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
