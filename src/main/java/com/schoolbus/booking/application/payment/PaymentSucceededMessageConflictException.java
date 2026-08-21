package com.schoolbus.booking.application.payment;

public final class PaymentSucceededMessageConflictException
        extends RuntimeException {

    public PaymentSucceededMessageConflictException(String message) {
        super(message);
    }
}
