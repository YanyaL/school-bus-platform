package com.schoolbus.transport.api.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateDraftTripRequest(

        @NotBlank(message = "vehicleNo must not be blank")
        String vehicleNo,

        @NotBlank(message = "routeNo must not be blank")
        String routeNo,

        @NotNull(message = "departureTime must not be null")
        Instant departureTime,

        @NotNull(message = "bookingDeadline must not be null")
        Instant bookingDeadline,

        @NotNull(message = "price must not be null")
        @DecimalMin(value = "0.00", message = "price must not be negative")
        BigDecimal price
) {
}
