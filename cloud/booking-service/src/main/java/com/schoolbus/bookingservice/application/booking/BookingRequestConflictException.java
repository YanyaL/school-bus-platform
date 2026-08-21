package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingRequestNumber;
import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;

public final class BookingRequestConflictException
        extends BusinessException {

    public BookingRequestConflictException(
            BookingRequestNumber requestNumber
    ) {
        super(
                ErrorCode.BOOKING_REQUEST_CONFLICT,
                "requestNumber has already been used for another booking: "
                        + requestNumber.value()
        );
    }
}
