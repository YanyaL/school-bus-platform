package com.schoolbus.bookingservice.application.trippublication;

/** A permanent contract/identity conflict; never retry it as a database outage. */
public class TripPublicationRejectedException extends RuntimeException {
    public TripPublicationRejectedException(String message) { super(message); }
    public TripPublicationRejectedException(String message, Throwable cause) { super(message, cause); }
}
