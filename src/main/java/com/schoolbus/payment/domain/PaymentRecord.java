package com.schoolbus.payment.domain;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingNumber;

import java.time.Instant;
import java.util.Objects;

public final class PaymentRecord {

    private final PaymentId paymentId;
    private final PaymentNumber paymentNumber;
    private final PaymentRequestNumber requestNumber;
    private final BookingNumber bookingNumber;
    private final BookingAmount amount;
    private final PaymentStatus status;
    private final String failureReason;
    private final Instant completedAt;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PaymentRecord(
            PaymentId paymentId,
            PaymentNumber paymentNumber,
            PaymentRequestNumber requestNumber,
            BookingNumber bookingNumber,
            BookingAmount amount,
            PaymentStatus status,
            String failureReason,
            Instant completedAt,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId must not be null");
        this.paymentNumber = Objects.requireNonNull(paymentNumber, "paymentNumber must not be null");
        this.requestNumber = Objects.requireNonNull(requestNumber, "requestNumber must not be null");
        this.bookingNumber = Objects.requireNonNull(bookingNumber, "bookingNumber must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.failureReason = normalizeFailureReason(failureReason);
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (version < 0L) {
            throw new IllegalArgumentException("version must not be negative");
        }
        if (status == PaymentStatus.REFUND_PENDING && this.failureReason == null) {
            throw new IllegalArgumentException("refund pending payment requires a reason");
        }
        if (status == PaymentStatus.SUCCEEDED && this.failureReason != null) {
            throw new IllegalArgumentException("succeeded payment must not have a failure reason");
        }
        this.version = version;
    }

    public static PaymentRecord succeeded(
            PaymentId paymentId,
            PaymentNumber paymentNumber,
            PaymentRequestNumber requestNumber,
            BookingNumber bookingNumber,
            BookingAmount amount,
            Instant paidAt,
            Instant recordedAt
    ) {
        return new PaymentRecord(
                paymentId, paymentNumber, requestNumber, bookingNumber,
                amount, PaymentStatus.SUCCEEDED, null, paidAt,
                0L, recordedAt, recordedAt
        );
    }

    public static PaymentRecord refundPending(
            PaymentId paymentId,
            PaymentNumber paymentNumber,
            PaymentRequestNumber requestNumber,
            BookingNumber bookingNumber,
            BookingAmount amount,
            String reason,
            Instant paidAt,
            Instant recordedAt
    ) {
        return new PaymentRecord(
                paymentId, paymentNumber, requestNumber, bookingNumber,
                amount, PaymentStatus.REFUND_PENDING, reason, paidAt,
                0L, recordedAt, recordedAt
        );
    }

    public static PaymentRecord restore(
            PaymentId paymentId,
            PaymentNumber paymentNumber,
            PaymentRequestNumber requestNumber,
            BookingNumber bookingNumber,
            BookingAmount amount,
            PaymentStatus status,
            String failureReason,
            Instant completedAt,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PaymentRecord(
                paymentId, paymentNumber, requestNumber, bookingNumber,
                amount, status, failureReason, completedAt, version,
                createdAt, updatedAt
        );
    }

    private static String normalizeFailureReason(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new IllegalArgumentException("failureReason length must be between 1 and 255");
        }
        return normalized;
    }

    public PaymentId paymentId() { return paymentId; }
    public PaymentNumber paymentNumber() { return paymentNumber; }
    public PaymentRequestNumber requestNumber() { return requestNumber; }
    public BookingNumber bookingNumber() { return bookingNumber; }
    public BookingAmount amount() { return amount; }
    public PaymentStatus status() { return status; }
    public String failureReason() { return failureReason; }
    public Instant completedAt() { return completedAt; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
