package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;

public final class BookingNotFoundException extends BusinessException {

    public BookingNotFoundException(BookingNumber bookingNumber) {
        super(
                ErrorCode.RESOURCE_NOT_FOUND,
                "booking not found: " + bookingNumber
        );
    }
}
