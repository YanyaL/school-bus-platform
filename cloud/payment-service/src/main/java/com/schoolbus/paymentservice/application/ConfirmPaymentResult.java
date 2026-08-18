package com.schoolbus.paymentservice.application;

import java.math.BigDecimal;
import java.time.Instant;

public record ConfirmPaymentResult(
        long paymentId,
        String paymentNumber,
        String bookingNumber,
        BigDecimal amount,
        PaymentConfirmationOutcome outcome,
        Instant paidAt
) {
}
