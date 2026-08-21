package com.schoolbus.booking.application.payment;

public record PaymentSucceededResult(
        String paymentNumber,
        String bookingNumber,
        PaymentSucceededOutcome outcome
) {
}
