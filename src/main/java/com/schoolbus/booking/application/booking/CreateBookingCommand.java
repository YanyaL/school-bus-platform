package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.PublicTripNumber;
import com.schoolbus.shared.domain.identity.UserId;

public record CreateBookingCommand(
        long userId,
        String tripNumber,
        String seatNumber,
        String requestNumber
) {

    public CreateBookingCommand {
        UserId.of(userId);
        tripNumber = PublicTripNumber.of(tripNumber).toString();
        seatNumber = SeatNumber.of(seatNumber).value();
        requestNumber = BookingRequestNumber
                .of(requestNumber)
                .value();
    }
}
