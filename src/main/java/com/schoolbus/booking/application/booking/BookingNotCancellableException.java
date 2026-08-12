package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

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
