package com.schoolbus.bookingservice.application.payment;

import java.util.Objects;

public record PaymentRefundedResult(
        String paymentNumber,
        String bookingNumber,
        PaymentRefundedOutcome outcome
) {

    public PaymentRefundedResult {
        Objects.requireNonNull(paymentNumber, "paymentNumber must not be null");
        Objects.requireNonNull(bookingNumber, "bookingNumber must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
    }
}
