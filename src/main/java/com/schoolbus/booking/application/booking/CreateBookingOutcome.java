package com.schoolbus.booking.application.booking;

import java.util.Objects;

public record CreateBookingOutcome(
        CreateBookingResult result,
        boolean idempotencyReplayed
) {

    public CreateBookingOutcome {
        Objects.requireNonNull(result, "result must not be null");
    }
}
