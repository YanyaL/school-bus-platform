package com.schoolbus.payment.application;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.payment.domain.PaymentNumber;

import java.time.Instant;
import java.util.Objects;

public record RefundRequiredEvent(
        PaymentNumber paymentNumber,
        BookingNumber bookingNumber,
        BookingAmount amount,
        String reason,
        Instant paidAt,
        Instant occurredAt
) {
    public RefundRequiredEvent {
        Objects.requireNonNull(paymentNumber, "paymentNumber must not be null");
        Objects.requireNonNull(bookingNumber, "bookingNumber must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        String validatedReason = Objects.requireNonNull(reason, "reason must not be null").strip();
        if (validatedReason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        reason = validatedReason;
        Objects.requireNonNull(paidAt, "paidAt must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
