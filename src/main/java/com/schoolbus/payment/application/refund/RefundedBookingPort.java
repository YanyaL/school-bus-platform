package com.schoolbus.payment.application.refund;

import com.schoolbus.booking.domain.order.BookingNumber;

import java.time.Instant;

public interface RefundedBookingPort {

    void markRefunded(BookingNumber bookingNumber, Instant refundedAt);
}
