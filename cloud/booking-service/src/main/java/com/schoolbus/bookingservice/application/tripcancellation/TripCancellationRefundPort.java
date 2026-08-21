package com.schoolbus.bookingservice.application.tripcancellation;

import com.schoolbus.bookingservice.domain.order.BookingOrder;

import java.time.Instant;

public interface TripCancellationRefundPort {

    void requestRefund(BookingOrder order, Instant requestedAt);
}
