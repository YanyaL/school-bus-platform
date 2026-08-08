package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;

public record CreateBookingCommand(
        long userId,
        long tripId,
        String seatNumber,
        String requestNumber
) {

    public CreateBookingCommand {
        UserId.of(userId);
        TripReference.of(tripId);
        seatNumber = SeatNumber.of(seatNumber).value();
        requestNumber = BookingRequestNumber
                .of(requestNumber)
                .value();
    }
}
