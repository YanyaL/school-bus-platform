package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.trip.PublicTripNumber;

import java.util.Optional;

public interface BookableTripGateway {

    Optional<BookableTripSnapshot> findByTripNumber(
            PublicTripNumber tripNumber
    );
}
