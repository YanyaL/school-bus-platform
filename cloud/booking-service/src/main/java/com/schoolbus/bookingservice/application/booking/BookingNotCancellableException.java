package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.BookingStatus;
import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;

public final class BookingNotCancellableException extends BusinessException {

    public BookingNotCancellableException(
            BookingNumber bookingNumber,
            BookingStatus status
    ) {
        super(
                ErrorCode.BOOKING_NOT_CANCELLABLE,
                "booking " + bookingNumber + " cannot be cancelled in status "
                        + status
        );
    }
}
