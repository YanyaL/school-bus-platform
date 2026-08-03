package com.schoolbus.transport.application.trip;

public record TripStatusUpdateResult(
        int closedBookings,
        int departedTrips,
        int optimisticLockConflicts
) {

    public TripStatusUpdateResult {
        if (closedBookings < 0
                || departedTrips < 0
                || optimisticLockConflicts < 0) {
            throw new IllegalArgumentException(
                    "trip status update counts must not be negative"
            );
        }
    }
}
