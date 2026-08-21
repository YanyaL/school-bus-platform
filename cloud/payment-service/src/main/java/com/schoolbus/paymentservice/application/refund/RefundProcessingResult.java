package com.schoolbus.paymentservice.application.refund;

public record RefundProcessingResult(
        RefundProcessingOutcome outcome,
        String paymentNumber,
        String refundReference
) {
}
