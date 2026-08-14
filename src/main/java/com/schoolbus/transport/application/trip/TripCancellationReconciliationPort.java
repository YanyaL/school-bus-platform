package com.schoolbus.transport.application.trip;

import java.time.Instant;
import java.util.List;

public interface TripCancellationReconciliationPort {

    List<Long> findSettledCancellationsAwaitingFinalization(
            Instant settledBefore,
            int limit
    );
}
