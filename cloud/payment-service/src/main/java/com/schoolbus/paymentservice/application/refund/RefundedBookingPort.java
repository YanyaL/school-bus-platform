package com.schoolbus.paymentservice.application.refund;

import com.schoolbus.paymentservice.domain.BookingNumber;

import java.time.Instant;

public interface RefundedBookingPort {

    void markRefunded(BookingNumber bookingNumber, Instant refundedAt);
}
