package com.schoolbus.bookingservice.application.tripcancellation;

import java.time.Instant;

public interface TripCancellationProgressPort {

    boolean start(
            long tripId,
            String requestEventId,
            int pendingRefunds,
            Instant startedAt
    );

    boolean completeRefund(long tripId, Instant completedAt);
}
