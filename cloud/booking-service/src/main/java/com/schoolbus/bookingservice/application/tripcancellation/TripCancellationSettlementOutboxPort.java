package com.schoolbus.bookingservice.application.tripcancellation;

public interface TripCancellationSettlementOutboxPort {

    void append(TripCancellationBookingsSettledEvent event);
}
