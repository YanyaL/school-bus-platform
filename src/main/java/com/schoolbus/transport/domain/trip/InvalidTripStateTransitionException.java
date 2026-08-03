package com.schoolbus.transport.domain.trip;

public final class InvalidTripStateTransitionException
        extends RuntimeException {

    public InvalidTripStateTransitionException(
            TripStatus currentStatus,
            TripStatus targetStatus
    ) {
        super(
                "cannot change trip status from "
                        + currentStatus
                        + " to "
                        + targetStatus
        );
    }
}
