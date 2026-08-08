package com.schoolbus.booking.domain.order;

import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;

import java.time.Instant;
import java.util.Objects;

public final class BookingOrder {

    private final BookingId bookingId;
    private final UserId userId;
    private final TripReference tripReference;
    private final BookingAmount amount;
    private BookingStatus status;
    private final Instant expiresAt;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private BookingOrder(
            BookingId bookingId,
            UserId userId,
            TripReference tripReference,
            BookingAmount amount,
            BookingStatus status,
            Instant expiresAt,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.bookingId = Objects.requireNonNull(
                bookingId,
                "bookingId must not be null"
        );
        this.userId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        this.tripReference = Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
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
            UserId userId,
            TripReference tripReference,
            BookingAmount amount,
            Instant expiresAt,
            Instant placedAt
    ) {
        return new BookingOrder(
                bookingId,
                userId,
                tripReference,
                amount,
                BookingStatus.PENDING_PAYMENT,
                expiresAt,
                0L,
                placedAt,
                placedAt
        );
    }

    public static BookingOrder restore(
            BookingId bookingId,
            UserId userId,
            TripReference tripReference,
            BookingAmount amount,
            BookingStatus status,
            Instant expiresAt,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new BookingOrder(
                bookingId,
                userId,
                tripReference,
                amount,
                status,
                expiresAt,
                version,
                createdAt,
                updatedAt
        );
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

    public TripReference tripReference() {
        return tripReference;
    }

    public BookingAmount amount() {
        return amount;
    }

    public BookingStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
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
