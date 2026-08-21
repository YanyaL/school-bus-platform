package com.schoolbus.bookingservice.application.payment;

public record PaymentSucceededResult(
        String paymentNumber,
        String bookingNumber,
        PaymentSucceededOutcome outcome
) {
}
