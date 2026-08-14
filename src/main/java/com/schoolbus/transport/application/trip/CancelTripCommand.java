package com.schoolbus.transport.application.trip;

public record CancelTripCommand(
        long tripId,
        long expectedVersion
) {
}
