package com.schoolbus.payment.api;

import com.schoolbus.payment.application.ConfirmPaymentCommand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCallbackRequest(
        String requestNumber,
        String paymentNumber,
        String bookingNumber,
        BigDecimal amount,
        Instant paidAt
) {
    ConfirmPaymentCommand toCommand() {
        try {
            if (requestNumber == null
                    || requestNumber.isBlank()
                    || requestNumber.strip().length() > 64
                    || amount == null
                    || amount.signum() < 0
                    || paidAt == null) {
                throw new IllegalArgumentException();
            }
            UUID.fromString(paymentNumber);
            UUID.fromString(bookingNumber);
            return new ConfirmPaymentCommand(
                    requestNumber,
                    paymentNumber,
                    bookingNumber,
                    amount,
                    paidAt
            );
        } catch (RuntimeException exception) {
            throw new MalformedPaymentCallbackException();
        }
    }
}
