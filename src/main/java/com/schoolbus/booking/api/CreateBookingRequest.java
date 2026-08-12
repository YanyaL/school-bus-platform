package com.schoolbus.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateBookingRequest(

        @NotNull(message = "tripId must not be null")
        @Positive(message = "tripId must be positive")
        Long tripId,

        @NotBlank(message = "seatNumber must not be blank")
        @Pattern(
                regexp = "^[A-Za-z0-9-]{1,10}$",
                message = "seatNumber must contain 1 to 10 letters, digits or hyphens"
        )
        String seatNumber
) {
}
