package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

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
