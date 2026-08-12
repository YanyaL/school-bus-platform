package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class BookingNotFoundException extends BusinessException {

    public BookingNotFoundException(BookingNumber bookingNumber) {
        super(
                ErrorCode.RESOURCE_NOT_FOUND,
                "booking not found: " + bookingNumber
        );
    }
}
