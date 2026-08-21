package com.schoolbus.paymentservice.infrastructure.messaging;

public final class MalformedRefundMessageException
        extends RuntimeException {

    public MalformedRefundMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
