package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingRequestNumber;
import com.schoolbus.bookingservice.domain.order.SeatNumber;
import com.schoolbus.bookingservice.domain.trip.PublicTripNumber;
import com.schoolbus.bookingservice.shared.domain.identity.UserId;

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
