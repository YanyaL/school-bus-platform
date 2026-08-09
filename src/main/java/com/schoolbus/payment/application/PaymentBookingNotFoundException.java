package com.schoolbus.payment.application;

import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public final class PaymentBookingNotFoundException extends BusinessException {
    public PaymentBookingNotFoundException(BookingNumber bookingNumber) {
        super(ErrorCode.PAYMENT_BOOKING_NOT_FOUND,
                "booking does not exist: " + bookingNumber);
    }
}
