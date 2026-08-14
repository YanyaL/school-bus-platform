package com.schoolbus.transport.infrastructure.messaging;

public class TripCancellationRetryPublishException
        extends RuntimeException {

    public TripCancellationRetryPublishException(String message) {
        super(message);
    }

    public TripCancellationRetryPublishException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
