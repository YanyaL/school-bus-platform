package com.schoolbus.transport.application.trip;

public interface TripCancellationOutboxPort {

    void append(TripCancellationRequestedEvent event);
}
