package com.schoolbus.transport.domain.trip;

import java.time.Instant;
import java.util.Objects;

public final class BusTrip {

    private final TripId tripId;
    private final RouteId routeId;
    private final Instant departureAt;
    private final Instant arrivalAt;
    private TripStatus status;
    private SeatCapacity seatCapacity;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private BusTrip(
            TripId tripId,
            RouteId routeId,
            Instant departureAt,
            Instant arrivalAt,
            TripStatus status,
            SeatCapacity seatCapacity,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.tripId = Objects.requireNonNull(
                tripId,
                "tripId must not be null"
        );
        this.routeId = Objects.requireNonNull(
                routeId,
                "routeId must not be null"
        );
        this.departureAt = Objects.requireNonNull(
                departureAt,
                "departureAt must not be null"
        );
        this.arrivalAt = Objects.requireNonNull(
                arrivalAt,
                "arrivalAt must not be null"
        );
        if (!arrivalAt.isAfter(departureAt)) {
            throw new IllegalArgumentException(
                    "arrivalAt must be after departureAt"
            );
        }
        this.status = Objects.requireNonNull(
                status,
                "status must not be null"
        );
        this.seatCapacity = Objects.requireNonNull(
                seatCapacity,
                "seatCapacity must not be null"
        );
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

    public static BusTrip schedule(
            TripId tripId,
            RouteId routeId,
            Instant departureAt,
            Instant arrivalAt,
            int totalSeats,
            Instant scheduledAt
    ) {
        return new BusTrip(
                tripId,
                routeId,
                departureAt,
                arrivalAt,
                TripStatus.SCHEDULED,
                SeatCapacity.full(totalSeats),
                0L,
                scheduledAt,
                scheduledAt
        );
    }

    public static BusTrip restore(
            TripId tripId,
            RouteId routeId,
            Instant departureAt,
            Instant arrivalAt,
            TripStatus status,
            SeatCapacity seatCapacity,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new BusTrip(
                tripId,
                routeId,
                departureAt,
                arrivalAt,
                status,
                seatCapacity,
                version,
                createdAt,
                updatedAt
        );
    }

    public void reserveSeat(Instant reservedAt) {
        ensureSeatOperationAllowed();
        Instant operationTime = validateChangeTime(reservedAt);
        SeatCapacity updatedCapacity = seatCapacity.reserveOne();
        seatCapacity = updatedCapacity;
        recordChange(operationTime);
    }

    public void releaseSeat(Instant releasedAt) {
        ensureSeatOperationAllowed();
        Instant operationTime = validateChangeTime(releasedAt);
        SeatCapacity updatedCapacity = seatCapacity.releaseOne();
        seatCapacity = updatedCapacity;
        recordChange(operationTime);
    }

    public void startBoarding(Instant startedAt) {
        transitionTo(TripStatus.BOARDING, startedAt);
    }

    public void depart(Instant departedAt) {
        transitionTo(TripStatus.DEPARTED, departedAt);
    }

    public void arrive(Instant arrivedAt) {
        transitionTo(TripStatus.ARRIVED, arrivedAt);
    }

    public void cancel(Instant cancelledAt) {
        transitionTo(TripStatus.CANCELLED, cancelledAt);
    }

    public boolean canReserve() {
        return allowsSeatOperation()
                && seatCapacity.hasAvailableSeat();
    }

    private void transitionTo(
            TripStatus targetStatus,
            Instant changedAt
    ) {
        if (!isTransitionAllowed(status, targetStatus)) {
            throw new InvalidTripStateTransitionException(
                    status,
                    targetStatus
            );
        }
        Instant operationTime = validateChangeTime(changedAt);
        status = targetStatus;
        recordChange(operationTime);
    }

    private boolean isTransitionAllowed(
            TripStatus currentStatus,
            TripStatus targetStatus
    ) {
        return switch (currentStatus) {
            case SCHEDULED -> targetStatus == TripStatus.BOARDING
                    || targetStatus == TripStatus.CANCELLED;
            case BOARDING -> targetStatus == TripStatus.DEPARTED
                    || targetStatus == TripStatus.CANCELLED;
            case DEPARTED -> targetStatus == TripStatus.ARRIVED;
            case CANCELLED, ARRIVED -> false;
        };
    }

    private void ensureSeatOperationAllowed() {
        if (!allowsSeatOperation()) {
            throw new TripSeatOperationNotAllowedException(status);
        }
    }

    private boolean allowsSeatOperation() {
        return status == TripStatus.SCHEDULED
                || status == TripStatus.BOARDING;
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

    private void recordChange(Instant operationTime) {
        updatedAt = operationTime;
        version++;
    }

    public TripId tripId() {
        return tripId;
    }

    public RouteId routeId() {
        return routeId;
    }

    public Instant departureAt() {
        return departureAt;
    }

    public Instant arrivalAt() {
        return arrivalAt;
    }

    public TripStatus status() {
        return status;
    }

    public SeatCapacity seatCapacity() {
        return seatCapacity;
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
