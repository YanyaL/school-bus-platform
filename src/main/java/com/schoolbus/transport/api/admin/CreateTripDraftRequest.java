package com.schoolbus.transport.api.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateTripDraftRequest(

        @NotBlank(message = "vehicleId must not be blank")
        @Size(max = 19, message = "vehicleId must not exceed 19 digits")
        @Pattern(
                regexp = "^[1-9][0-9]{0,18}$",
                message = "vehicleId must be a positive decimal integer string"
        )
        String vehicleId,

        @NotBlank(message = "routeId must not be blank")
        @Size(max = 19, message = "routeId must not exceed 19 digits")
        @Pattern(
                regexp = "^[1-9][0-9]{0,18}$",
                message = "routeId must be a positive decimal integer string"
        )
        String routeId,

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
