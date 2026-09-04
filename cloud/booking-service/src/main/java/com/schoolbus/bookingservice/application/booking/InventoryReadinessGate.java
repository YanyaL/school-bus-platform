package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.trip.TripReference;

public interface InventoryReadinessGate {

    boolean isReady(TripReference tripReference, long tripVersion);
}
