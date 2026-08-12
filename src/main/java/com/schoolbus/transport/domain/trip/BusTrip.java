package com.schoolbus.transport.domain.trip;

import com.schoolbus.transport.domain.vehicle.VehicleId;

import java.time.Instant;
import java.util.Objects;

public final class BusTrip {

    private final TripId tripId;
    private final TripNumber tripNumber;
    private final VehicleId vehicleId;
    private final RouteId routeId;
    private final Instant departureTime;
    private final Instant bookingDeadline;
    private final Money price;
    private TripStatus status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private BusTrip(
            TripId tripId,
            TripNumber tripNumber,
            VehicleId vehicleId,
            RouteId routeId,
            Instant departureTime,
            Instant bookingDeadline,
            Money price,
            TripStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.tripId = Objects.requireNonNull(
                tripId,
                "tripId must not be null"
        );
        this.tripNumber = Objects.requireNonNull(
                tripNumber,
                "tripNumber must not be null"
        );
        this.vehicleId = Objects.requireNonNull(
                vehicleId,
                "vehicleId must not be null"
        );
        this.routeId = Objects.requireNonNull(
                routeId,
                "routeId must not be null"
        );
        this.departureTime = Objects.requireNonNull(
                departureTime,
                "departureTime must not be null"
        );
        this.bookingDeadline = Objects.requireNonNull(
                bookingDeadline,
                "bookingDeadline must not be null"
        );
        if (!bookingDeadline.isBefore(departureTime)) {
            throw new IllegalArgumentException(
                    "bookingDeadline must be before departureTime"
            );
        }
        this.price = Objects.requireNonNull(
                price,
                "price must not be null"
        );
        this.status = Objects.requireNonNull(
                status,
                "status must not be null"
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

    public static BusTrip draft(
            TripId tripId,
            TripNumber tripNumber,
            VehicleId vehicleId,
            RouteId routeId,
            Instant departureTime,
            Instant bookingDeadline,
            Money price,
            Instant createdAt
    ) {
        return new BusTrip(
                tripId,
                tripNumber,
                vehicleId,
                routeId,
                departureTime,
                bookingDeadline,
                price,
                TripStatus.DRAFT,
                0L,
                createdAt,
                createdAt
        );
    }

    public static BusTrip restore(
            TripId tripId,
            TripNumber tripNumber,
            VehicleId vehicleId,
            RouteId routeId,
            Instant departureTime,
            Instant bookingDeadline,
            Money price,
            TripStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new BusTrip(
                tripId,
                tripNumber,
                vehicleId,
                routeId,
                departureTime,
                bookingDeadline,
                price,
                status,
                version,
                createdAt,
                updatedAt
        );
    }

    public void openForBooking(Instant openedAt) {
        transitionTo(TripStatus.OPEN_FOR_BOOKING, openedAt);
    }

    public void closeBooking(Instant closedAt) {
        transitionTo(TripStatus.CLOSED, closedAt);
    }

    public void depart(Instant departedAt) {
        transitionTo(TripStatus.DEPARTED, departedAt);
    }

    public void complete(Instant completedAt) {
        transitionTo(TripStatus.COMPLETED, completedAt);
    }

    public void cancel(Instant cancelledAt) {
        transitionTo(TripStatus.CANCELLED, cancelledAt);
    }

    public boolean canBookAt(Instant instant) {
        Instant checkedInstant = Objects.requireNonNull(
                instant,
                "instant must not be null"
        );
        return status == TripStatus.OPEN_FOR_BOOKING
                && checkedInstant.isBefore(bookingDeadline);
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
        updatedAt = operationTime;
        version++;
    }

    private boolean isTransitionAllowed(
            TripStatus currentStatus,
            TripStatus targetStatus
    ) {
        return switch (currentStatus) {
            case DRAFT -> targetStatus == TripStatus.OPEN_FOR_BOOKING
                    || targetStatus == TripStatus.CANCELLED;
            case OPEN_FOR_BOOKING -> targetStatus == TripStatus.CLOSED
                    || targetStatus == TripStatus.CANCELLED;
            case CLOSED -> targetStatus == TripStatus.DEPARTED
                    || targetStatus == TripStatus.CANCELLED;
            case DEPARTED -> targetStatus == TripStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
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

    public TripId tripId() {
        return tripId;
    }

    public TripNumber tripNumber() {
        return tripNumber;
    }

    public VehicleId vehicleId() {
        return vehicleId;
    }

    public RouteId routeId() {
        return routeId;
    }

    public Instant departureTime() {
        return departureTime;
    }

    public Instant bookingDeadline() {
        return bookingDeadline;
    }

    public Money price() {
        return price;
    }

    public TripStatus status() {
        return status;
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
