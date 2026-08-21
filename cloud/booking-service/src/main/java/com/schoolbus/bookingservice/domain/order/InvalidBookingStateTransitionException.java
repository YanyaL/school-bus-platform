package com.schoolbus.bookingservice.domain.order;

public final class InvalidBookingStateTransitionException
        extends RuntimeException {

    public InvalidBookingStateTransitionException(
            BookingStatus currentStatus,
            BookingStatus targetStatus
    ) {
        super(
                "cannot change booking status from "
                        + currentStatus
                        + " to "
                        + targetStatus
        );
    }
}
