package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.trip.TripReference;

import java.util.Optional;

public interface BookableTripGateway {

    Optional<BookableTripSnapshot> findByTripReference(
            TripReference tripReference
    );
}
