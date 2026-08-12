package com.schoolbus.booking.application.booking;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class BookingNotCancellableException
        extends BusinessException {

    public BookingNotCancellableException() {
        super(ErrorCode.BOOKING_NOT_CANCELLABLE);
    }
}
