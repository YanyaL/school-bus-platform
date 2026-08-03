package com.schoolbus.transport.domain.trip;

public final class SeatCapacityExceededException
        extends RuntimeException {

    public SeatCapacityExceededException() {
        super("available seats cannot exceed total seats");
    }
}
