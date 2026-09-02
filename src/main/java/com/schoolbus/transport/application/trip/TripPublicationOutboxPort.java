package com.schoolbus.transport.application.trip;

public interface TripPublicationOutboxPort {
    /** Must join the publication transaction; never send to a broker here. */
    void append(TripPublishedEvent event);
}
