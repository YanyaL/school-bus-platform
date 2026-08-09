package com.schoolbus.payment.api;

import com.schoolbus.payment.application.ConfirmPaymentResult;
import com.schoolbus.payment.application.PaymentConfirmationOutcome;

public record PaymentCallbackResponse(
        String paymentNumber,
        String bookingNumber,
        PaymentConfirmationOutcome outcome
) {
    static PaymentCallbackResponse from(ConfirmPaymentResult result) {
        return new PaymentCallbackResponse(
                result.paymentNumber(),
                result.bookingNumber(),
                result.outcome()
        );
    }
}
