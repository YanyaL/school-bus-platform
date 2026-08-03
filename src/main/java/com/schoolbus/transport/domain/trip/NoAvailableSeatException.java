package com.schoolbus.transport.domain.trip;

public final class NoAvailableSeatException
        extends RuntimeException {

    public NoAvailableSeatException() {
        super("no available seat");
    }
}
