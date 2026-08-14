package com.schoolbus.transport.api.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateTripDraftRequest(

        @NotNull(message = "vehicleId must not be null")
        @Positive(message = "vehicleId must be positive")
        Long vehicleId,

        @NotNull(message = "routeId must not be null")
        @Positive(message = "routeId must be positive")
        Long routeId,

        @NotNull(message = "departureTime must not be null")
        @Future(message = "departureTime must be in the future")
        Instant departureTime,

        @NotNull(message = "bookingDeadline must not be null")
        @Future(message = "bookingDeadline must be in the future")
        Instant bookingDeadline,

        @NotNull(message = "price must not be null")
        @DecimalMin(value = "0.00", message = "price must not be negative")
        @Digits(
                integer = 8,
                fraction = 2,
                message = "price must have at most 8 integer and 2 decimal digits"
        )
        BigDecimal price
) {
}
