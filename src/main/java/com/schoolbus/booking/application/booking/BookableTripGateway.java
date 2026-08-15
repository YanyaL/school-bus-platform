package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.trip.PublicTripNumber;

import java.util.Optional;

public interface BookableTripGateway {

    Optional<BookableTripSnapshot> findByTripNumber(
            PublicTripNumber tripNumber
    );
}
