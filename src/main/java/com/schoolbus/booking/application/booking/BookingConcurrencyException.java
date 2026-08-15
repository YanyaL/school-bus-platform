package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.trip.PublicTripNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class BookingConcurrencyException
        extends BusinessException {

    public BookingConcurrencyException(
            TripReference tripReference
    ) {
        this(String.valueOf(tripReference.value()));
    }

    public BookingConcurrencyException(
            PublicTripNumber tripNumber
    ) {
        this(tripNumber.toString());
    }

    private BookingConcurrencyException(String trip) {
        super(
                ErrorCode.BOOKING_CONCURRENCY_CONFLICT,
                "booking could not be completed after concurrent "
                        + "updates for trip: "
                        + trip
        );
    }
}
