package com.schoolbus.payment.application.refund;

public final class RefundPaymentNotFoundException
        extends RuntimeException {

    public RefundPaymentNotFoundException(String paymentNumber) {
        super("refund payment was not found: " + paymentNumber);
    }
}
