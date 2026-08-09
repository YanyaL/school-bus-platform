package com.schoolbus.booking.domain.order;

import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;

import java.time.Instant;
import java.util.Objects;

public final class BookingOrder {

    private final BookingId bookingId;
    private final BookingNumber bookingNumber;
    private final BookingRequestNumber requestNumber;
    private final UserId userId;
    private final TripReference tripReference;
    private final SeatNumber seatNumber;
    private final BookingAmount amount;
    private BookingStatus status;
    private final Instant expiresAt;
    private PaymentReference paymentReference;
    private Instant paidAt;
    private Instant cancelledAt;
    private CancellationReason cancellationReason;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private BookingOrder(
            BookingId bookingId,
            BookingNumber bookingNumber,
            BookingRequestNumber requestNumber,
            UserId userId,
            TripReference tripReference,
            SeatNumber seatNumber,
            BookingAmount amount,
            BookingStatus status,
            Instant expiresAt,
            PaymentReference paymentReference,
            Instant paidAt,
            Instant cancelledAt,
            CancellationReason cancellationReason,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.bookingId = Objects.requireNonNull(
                bookingId,
                "bookingId must not be null"
        );
        this.bookingNumber = Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        this.requestNumber = Objects.requireNonNull(
                requestNumber,
                "requestNumber must not be null"
        );
        this.userId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        this.tripReference = Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
        );
        this.seatNumber = Objects.requireNonNull(
                seatNumber,
                "seatNumber must not be null"
        );
        this.amount = Objects.requireNonNull(
                amount,
                "amount must not be null"
        );
        this.status = Objects.requireNonNull(
                status,
                "status must not be null"
        );
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
        this.expiresAt = Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
        );
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after createdAt"
            );
        }
        if ((paymentReference == null) != (paidAt == null)) {
            throw new IllegalArgumentException(
                    "paymentReference and paidAt must both be present or absent"
            );
        }
        if ((status == BookingStatus.PAID
                || status == BookingStatus.REFUNDED)
                && paymentReference == null) {
            throw new IllegalArgumentException(
                    "paid or refunded booking must contain payment details"
            );
        }
        if (status != BookingStatus.PAID
                && status != BookingStatus.REFUNDED
                && paymentReference != null) {
            throw new IllegalArgumentException(
                    "only paid or refunded booking may contain payment details"
            );
        }
        if (paidAt != null && paidAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "paidAt must not be before createdAt"
            );
        }
        this.paymentReference = paymentReference;
        this.paidAt = paidAt;
        if ((cancelledAt == null) != (cancellationReason == null)) {
            throw new IllegalArgumentException(
                    "cancelledAt and cancellationReason must both be present or absent"
            );
        }
        if (status == BookingStatus.CANCELLED && cancelledAt == null) {
            throw new IllegalArgumentException(
                    "cancelled booking must contain cancellation details"
            );
        }
        if (status != BookingStatus.CANCELLED && cancelledAt != null) {
            throw new IllegalArgumentException(
                    "only cancelled booking may contain cancellation details"
            );
        }
        this.cancelledAt = cancelledAt;
        this.cancellationReason = cancellationReason;
        if (version < 0) {
            throw new IllegalArgumentException(
                    "version must not be negative"
            );
        }
        this.version = version;
        this.updatedAt = Objects.requireNonNull(
                updatedAt,
                "updatedAt must not be null"
        );
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "updatedAt must not be before createdAt"
            );
        }
    }

    public static BookingOrder place(
            BookingId bookingId,
            BookingNumber bookingNumber,
            BookingRequestNumber requestNumber,
            UserId userId,
            TripReference tripReference,
            SeatNumber seatNumber,
            BookingAmount amount,
            Instant expiresAt,
            Instant placedAt
    ) {
        return new BookingOrder(
                bookingId,
                bookingNumber,
                requestNumber,
                userId,
                tripReference,
                seatNumber,
                amount,
                BookingStatus.PENDING_PAYMENT,
                expiresAt,
                null,
                null,
                null,
                null,
                0L,
                placedAt,
                placedAt
        );
    }

    public static BookingOrder restore(
            BookingId bookingId,
            BookingNumber bookingNumber,
            BookingRequestNumber requestNumber,
            UserId userId,
            TripReference tripReference,
            SeatNumber seatNumber,
            BookingAmount amount,
            BookingStatus status,
            Instant expiresAt,
            PaymentReference paymentReference,
            Instant paidAt,
            Instant cancelledAt,
            CancellationReason cancellationReason,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new BookingOrder(
                bookingId,
                bookingNumber,
                requestNumber,
                userId,
                tripReference,
                seatNumber,
                amount,
                status,
                expiresAt,
                paymentReference,
                paidAt,
                cancelledAt,
                cancellationReason,
                version,
                createdAt,
                updatedAt
        );
    }

    public void confirmPayment(
            PaymentReference paymentReference,
            Instant paidAt,
            Instant confirmedAt
    ) {
        if (status != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateTransitionException(
                    status,
                    BookingStatus.PAID
            );
        }
        PaymentReference validatedReference = Objects.requireNonNull(
                paymentReference,
                "paymentReference must not be null"
        );
        Instant validatedPaidAt = Objects.requireNonNull(
                paidAt,
                "paidAt must not be null"
        );
        Instant operationTime = validateChangeTime(confirmedAt);
        if (validatedPaidAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "paidAt must not be before createdAt"
            );
        }
        if (!validatedPaidAt.isBefore(expiresAt)) {
            throw new IllegalStateException(
                    "booking payment window has expired"
            );
        }
        if (operationTime.isBefore(validatedPaidAt)) {
            throw new IllegalArgumentException(
                    "confirmedAt must not be before paidAt"
            );
        }
        status = BookingStatus.PAID;
        this.paymentReference = validatedReference;
        this.paidAt = validatedPaidAt;
        updatedAt = operationTime;
        version++;
    }

    public void cancel(Instant cancelledAt) {
        if (status != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateTransitionException(
                    status,
                    BookingStatus.CANCELLED
            );
        }
        Instant operationTime = validateChangeTime(cancelledAt);
        status = BookingStatus.CANCELLED;
        this.cancelledAt = operationTime;
        this.cancellationReason = CancellationReason.USER_CANCELLED;
        updatedAt = operationTime;
        version++;
    }

    public void expire(Instant expiredAt) {
        if (status != BookingStatus.PENDING_PAYMENT) {
            throw new InvalidBookingStateTransitionException(
                    status,
                    BookingStatus.CANCELLED
            );
        }
        Instant operationTime = validateChangeTime(expiredAt);
        if (operationTime.isBefore(expiresAt)) {
            throw new IllegalStateException(
                    "booking payment window has not expired"
            );
        }
        status = BookingStatus.CANCELLED;
        cancelledAt = operationTime;
        cancellationReason = CancellationReason.PAYMENT_TIMEOUT;
        updatedAt = operationTime;
        version++;
    }

    public boolean isPaymentExpiredAt(Instant instant) {
        Instant checkedInstant = Objects.requireNonNull(
                instant,
                "instant must not be null"
        );
        return status == BookingStatus.PENDING_PAYMENT
                && !checkedInstant.isBefore(expiresAt);
    }

    private Instant validateChangeTime(Instant changedAt) {
        Instant operationTime = Objects.requireNonNull(
                changedAt,
                "changedAt must not be null"
        );
        if (operationTime.isBefore(updatedAt)) {
            throw new IllegalArgumentException(
                    "changedAt must not be before updatedAt"
            );
        }
        return operationTime;
    }

    public BookingId bookingId() {
        return bookingId;
    }

    public UserId userId() {
        return userId;
    }

    public BookingNumber bookingNumber() {
        return bookingNumber;
    }

    public BookingRequestNumber requestNumber() {
        return requestNumber;
    }

    public TripReference tripReference() {
        return tripReference;
    }

    public BookingAmount amount() {
        return amount;
    }

    public SeatNumber seatNumber() {
        return seatNumber;
    }

    public BookingStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public PaymentReference paymentReference() {
        return paymentReference;
    }

    public Instant paidAt() {
        return paidAt;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public CancellationReason cancellationReason() {
        return cancellationReason;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
