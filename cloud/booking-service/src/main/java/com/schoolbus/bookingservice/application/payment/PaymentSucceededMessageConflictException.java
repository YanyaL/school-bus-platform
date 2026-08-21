package com.schoolbus.bookingservice.application.payment;

public final class PaymentSucceededMessageConflictException
        extends RuntimeException {

    public PaymentSucceededMessageConflictException(String message) {
        super(message);
    }
}
