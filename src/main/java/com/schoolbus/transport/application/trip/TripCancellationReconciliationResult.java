package com.schoolbus.transport.application.trip;

public record TripCancellationReconciliationResult(
        int scanned,
        int finalized,
        int alreadyFinalized
) {
}
