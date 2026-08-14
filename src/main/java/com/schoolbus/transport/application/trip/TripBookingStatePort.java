package com.schoolbus.transport.application.trip;

public interface TripBookingStatePort {

    boolean hasActiveBookings(long tripId);
}
