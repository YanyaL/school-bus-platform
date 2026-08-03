package com.schoolbus.transport.domain.trip;

public final class TripSeatOperationNotAllowedException
        extends RuntimeException {

    public TripSeatOperationNotAllowedException(
            TripStatus status
    ) {
        super(
                "seat operation is not allowed when trip status is "
                        + status
        );
    }
}
