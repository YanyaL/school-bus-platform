package com.schoolbus.transport.domain.vehicle;

import java.time.Instant;
import java.util.Objects;

public final class Vehicle {

    private final VehicleId id;
    private final VehicleNumber vehicleNumber;
    private final LicensePlate licensePlate;
    private final int seatCount;
    private VehicleStatus status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Vehicle(
            VehicleId id,
            VehicleNumber vehicleNumber,
            LicensePlate licensePlate,
            int seatCount,
            VehicleStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.vehicleNumber = Objects.requireNonNull(
                vehicleNumber,
                "vehicleNumber must not be null"
        );
        this.licensePlate = Objects.requireNonNull(
                licensePlate,
                "licensePlate must not be null"
        );
        SeatLayout.of(seatCount);
        this.seatCount = seatCount;
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
    }

    public static Vehicle create(
            VehicleNumber vehicleNumber,
            LicensePlate licensePlate,
            int seatCount,
            Instant createdAt
    ) {
        Instant operationTime = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
        return new Vehicle(
                null,
                vehicleNumber,
                licensePlate,
                seatCount,
                VehicleStatus.ENABLED,
                0L,
                operationTime,
                operationTime
        );
    }

    public static Vehicle restore(
            VehicleId id,
            VehicleNumber vehicleNumber,
            LicensePlate licensePlate,
            int seatCount,
            VehicleStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Vehicle(
                Objects.requireNonNull(id, "id must not be null"),
                vehicleNumber,
                licensePlate,
                seatCount,
                status,
                version,
                createdAt,
                updatedAt
        );
    }

    public void enable(Instant enabledAt) {
        Instant operationTime = Objects.requireNonNull(
                enabledAt,
                "enabledAt must not be null"
        );
        if (status == VehicleStatus.ENABLED) {
            throw new IllegalStateException(
                    "vehicle is already enabled"
            );
        }
        status = VehicleStatus.ENABLED;
        version++;
        updatedAt = operationTime;
    }

    public void disable(Instant disabledAt) {
        Instant operationTime = Objects.requireNonNull(
                disabledAt,
                "disabledAt must not be null"
        );
        if (status == VehicleStatus.DISABLED) {
            throw new IllegalStateException(
                    "vehicle is already disabled"
            );
        }
        status = VehicleStatus.DISABLED;
        version++;
        updatedAt = operationTime;
    }

    public SeatLayout seatLayout() {
        return SeatLayout.of(seatCount);
    }

    public boolean isNew() {
        return id == null;
    }

    public VehicleId id() {
        return id;
    }

    public VehicleNumber vehicleNumber() {
        return vehicleNumber;
    }

    public LicensePlate licensePlate() {
        return licensePlate;
    }

    public int seatCount() {
        return seatCount;
    }

    public VehicleStatus status() {
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

    public Vehicle withId(VehicleId assignedId) {
        if (id != null) {
            throw new IllegalStateException(
                    "vehicle id has already been assigned"
            );
        }
        return new Vehicle(
                assignedId,
                vehicleNumber,
                licensePlate,
                seatCount,
                status,
                version,
                createdAt,
                updatedAt
        );
    }
}
