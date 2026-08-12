package com.schoolbus.transport.application.trip.admin;

import java.util.Objects;

public record PublishTripCommand(
        String tripNumber,
        long expectedVersion
) {

    public PublishTripCommand {
        if (tripNumber == null || tripNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "tripNumber must not be blank"
            );
        }
        tripNumber = tripNumber.strip();
        if (expectedVersion < 0L) {
            throw new IllegalArgumentException(
                    "expectedVersion must not be negative"
            );
        }
        Objects.requireNonNull(tripNumber, "tripNumber must not be null");
    }
}
