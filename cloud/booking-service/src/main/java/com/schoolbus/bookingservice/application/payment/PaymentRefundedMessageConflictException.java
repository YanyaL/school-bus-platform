package com.schoolbus.bookingservice.application.payment;

public final class PaymentRefundedMessageConflictException
        extends RuntimeException {

    public PaymentRefundedMessageConflictException(String message) {
        super(message);
    }
}
