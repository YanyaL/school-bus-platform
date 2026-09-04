package com.schoolbus.bookingservice.application.trippublication;

import java.util.Objects;

public record InventoryReadinessCandidate(
        long tripId,
        String tripNumber,
        long publicationVersion,
        String snapshotJson
) {
    public InventoryReadinessCandidate {
        if (tripId <= 0 || publicationVersion <= 0) {
            throw new IllegalArgumentException(
                    "positive trip identity and publication version required"
            );
        }
        if (Objects.requireNonNull(tripNumber).isBlank()) {
            throw new IllegalArgumentException("tripNumber must not be blank");
        }
        if (Objects.requireNonNull(snapshotJson).isBlank()) {
            throw new IllegalArgumentException("snapshotJson must not be blank");
        }
    }
}
