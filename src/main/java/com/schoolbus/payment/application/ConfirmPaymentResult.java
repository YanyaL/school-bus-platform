package com.schoolbus.payment.application;

import com.schoolbus.payment.domain.PaymentRecord;
import com.schoolbus.payment.domain.PaymentStatus;

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
    public static ConfirmPaymentResult from(PaymentRecord paymentRecord) {
        PaymentConfirmationOutcome outcome =
                paymentRecord.status() == PaymentStatus.SUCCEEDED
                        ? PaymentConfirmationOutcome.CONFIRMED
                        : PaymentConfirmationOutcome.REFUND_PENDING;
        return new ConfirmPaymentResult(
                paymentRecord.paymentId().value(),
                paymentRecord.paymentNumber().toString(),
                paymentRecord.bookingNumber().toString(),
                paymentRecord.amount().amount(),
                outcome,
                paymentRecord.completedAt()
        );
    }
}
