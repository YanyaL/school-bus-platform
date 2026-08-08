package com.schoolbus.booking.domain.inventory;

import com.schoolbus.booking.domain.trip.TripReference;

import java.time.Instant;
import java.util.Objects;

public final class SeatInventory {

    private final TripReference tripReference;
    private final int totalSeats;
    private int availableSeats;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private SeatInventory(
            TripReference tripReference,
            int totalSeats,
            int availableSeats,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.tripReference = Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
        );
        if (totalSeats <= 0) {
            throw new IllegalArgumentException(
                    "totalSeats must be positive"
            );
        }
        this.totalSeats = totalSeats;
        if (availableSeats < 0 || availableSeats > totalSeats) {
            throw new IllegalArgumentException(
                    "availableSeats must be between 0 and totalSeats"
            );
        }
        this.availableSeats = availableSeats;
        if (version < 0) {
            throw new IllegalArgumentException(
                    "version must not be negative"
            );
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
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

    public static SeatInventory initialize(
            TripReference tripReference,
            int totalSeats,
            Instant initializedAt
    ) {
        return new SeatInventory(
                tripReference,
                totalSeats,
                totalSeats,
                0L,
                initializedAt,
                initializedAt
        );
    }

    public static SeatInventory restore(
            TripReference tripReference,
            int totalSeats,
            int availableSeats,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new SeatInventory(
                tripReference,
                totalSeats,
                availableSeats,
                version,
                createdAt,
                updatedAt
        );
    }

    public void reserve(Instant reservedAt) {
        if (availableSeats == 0) {
            throw new NoSeatAvailableException(tripReference);
        }
        Instant operationTime = validateChangeTime(reservedAt);
        availableSeats--;
        updatedAt = operationTime;
        version++;
    }

    public void release(Instant releasedAt) {
        if (availableSeats == totalSeats) {
            throw new SeatInventoryOverflowException(
                    tripReference
            );
        }
        Instant operationTime = validateChangeTime(releasedAt);
        availableSeats++;
        updatedAt = operationTime;
        version++;
    }

    public boolean isSoldOut() {
        return availableSeats == 0;
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

    public TripReference tripReference() {
        return tripReference;
    }

    public int totalSeats() {
        return totalSeats;
    }

    public int availableSeats() {
        return availableSeats;
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
