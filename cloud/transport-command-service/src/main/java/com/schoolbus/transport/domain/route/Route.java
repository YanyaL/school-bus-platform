package com.schoolbus.transport.domain.route;

import java.time.Instant;
import java.util.Objects;

public final class Route {

    public static final int MAX_DURATION_MINUTES = 24 * 60;

    private final RouteId id;
    private final RouteNumber routeNumber;
    private final RouteCode routeCode;
    private final Campus departureCampus;
    private final Campus arrivalCampus;
    private final int estimatedDurationMinutes;
    private RouteStatus status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    private Route(
            RouteId id,
            RouteNumber routeNumber,
            RouteCode routeCode,
            Campus departureCampus,
            Campus arrivalCampus,
            int estimatedDurationMinutes,
            RouteStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.routeNumber = Objects.requireNonNull(
                routeNumber,
                "routeNumber must not be null"
        );
        this.routeCode = Objects.requireNonNull(
                routeCode,
                "routeCode must not be null"
        );
        this.departureCampus = Objects.requireNonNull(
                departureCampus,
                "departureCampus must not be null"
        );
        this.arrivalCampus = Objects.requireNonNull(
                arrivalCampus,
                "arrivalCampus must not be null"
        );
        validateDirection(departureCampus, arrivalCampus);
        validateDuration(estimatedDurationMinutes);
        this.estimatedDurationMinutes = estimatedDurationMinutes;
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
        this.id = id;
    }

    public static Route create(
            RouteNumber routeNumber,
            RouteCode routeCode,
            Campus departureCampus,
            Campus arrivalCampus,
            int estimatedDurationMinutes,
            Instant createdAt
    ) {
        Instant operationTime = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
        return new Route(
                null,
                routeNumber,
                routeCode,
                departureCampus,
                arrivalCampus,
                estimatedDurationMinutes,
                RouteStatus.ENABLED,
                0L,
                operationTime,
                operationTime
        );
    }

    public static Route restore(
            RouteId id,
            RouteNumber routeNumber,
            RouteCode routeCode,
            Campus departureCampus,
            Campus arrivalCampus,
            int estimatedDurationMinutes,
            RouteStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Route(
                Objects.requireNonNull(id, "id must not be null"),
                routeNumber,
                routeCode,
                departureCampus,
                arrivalCampus,
                estimatedDurationMinutes,
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
        if (status == RouteStatus.ENABLED) {
            throw new IllegalStateException(
                    "route is already enabled"
            );
        }
        status = RouteStatus.ENABLED;
        version++;
        updatedAt = operationTime;
    }

    public void disable(Instant disabledAt) {
        Instant operationTime = Objects.requireNonNull(
                disabledAt,
                "disabledAt must not be null"
        );
        if (status == RouteStatus.DISABLED) {
            throw new IllegalStateException(
                    "route is already disabled"
            );
        }
        status = RouteStatus.DISABLED;
        version++;
        updatedAt = operationTime;
    }

    public boolean isNew() {
        return id == null;
    }

    public RouteId id() {
        return id;
    }

    public RouteNumber routeNumber() {
        return routeNumber;
    }

    public RouteCode routeCode() {
        return routeCode;
    }

    public Campus departureCampus() {
        return departureCampus;
    }

    public Campus arrivalCampus() {
        return arrivalCampus;
    }

    public int estimatedDurationMinutes() {
        return estimatedDurationMinutes;
    }

    public RouteStatus status() {
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

    public Route withId(RouteId assignedId) {
        if (id != null) {
            throw new IllegalStateException(
                    "route id has already been assigned"
            );
        }
        return new Route(
                assignedId,
                routeNumber,
                routeCode,
                departureCampus,
                arrivalCampus,
                estimatedDurationMinutes,
                status,
                version,
                createdAt,
                updatedAt
        );
    }

    private static void validateDirection(
            Campus departureCampus,
            Campus arrivalCampus
    ) {
        if (departureCampus == arrivalCampus) {
            throw new IllegalArgumentException(
                    "departureCampus and arrivalCampus must differ"
            );
        }
    }

    private static void validateDuration(int estimatedDurationMinutes) {
        if (estimatedDurationMinutes <= 0
                || estimatedDurationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException(
                    "estimatedDurationMinutes must be between 1 and "
                            + MAX_DURATION_MINUTES
            );
        }
    }
}
