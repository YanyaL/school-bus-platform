package com.schoolbus.booking.application.tripcancellation;

import com.schoolbus.booking.domain.order.BookingOrder;

import java.time.Instant;

public interface TripCancellationRefundPort {

    void requestRefund(BookingOrder order, Instant requestedAt);
}
