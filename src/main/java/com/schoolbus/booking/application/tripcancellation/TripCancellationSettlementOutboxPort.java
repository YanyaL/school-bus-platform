package com.schoolbus.booking.application.tripcancellation;

public interface TripCancellationSettlementOutboxPort {

    void append(TripCancellationBookingsSettledEvent event);
}
