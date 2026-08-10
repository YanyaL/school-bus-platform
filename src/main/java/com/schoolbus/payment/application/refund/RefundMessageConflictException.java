package com.schoolbus.payment.application.refund;

public final class RefundMessageConflictException
        extends RuntimeException {

    public RefundMessageConflictException(String message) {
        super(message);
    }
}
