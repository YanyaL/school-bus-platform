package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.trip.PublicTripNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;

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
