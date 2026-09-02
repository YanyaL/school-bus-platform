package com.schoolbus.bookingservice.application.trippublication;

import java.time.Instant;
import java.util.Objects;

public record InventoryReadinessObservation(
        long tripId,
        String tripNumber,
        long publicationVersion,
        int expectedTotalSeats,
        Integer observedInventoryTotal,
        int observedSeatCount,
        Status status,
        String diagnosticCode,
        Instant checkedAt
) {
    public enum Status { WAITING, READY }

    public InventoryReadinessObservation {
        if (tripId <= 0 || publicationVersion <= 0 || expectedTotalSeats <= 0
                || observedSeatCount < 0) {
            throw new IllegalArgumentException("invalid readiness observation");
        }
        Objects.requireNonNull(tripNumber);
        Objects.requireNonNull(status);
        Objects.requireNonNull(checkedAt);
        if (status == Status.READY && diagnosticCode != null) {
            throw new IllegalArgumentException(
                    "ready observation cannot have a diagnostic code"
            );
        }
        if (status == Status.WAITING
                && (diagnosticCode == null || diagnosticCode.isBlank())) {
            throw new IllegalArgumentException(
                    "waiting observation requires a diagnostic code"
            );
        }
    }
}
