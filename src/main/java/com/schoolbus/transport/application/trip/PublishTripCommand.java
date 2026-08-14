package com.schoolbus.transport.application.trip;

public record PublishTripCommand(
        long tripId,
        long expectedVersion
) {
}
