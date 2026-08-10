package com.schoolbus.payment.application.refund;

public record RefundProcessingResult(
        RefundProcessingOutcome outcome,
        String paymentNumber,
        String refundReference
) {
}
