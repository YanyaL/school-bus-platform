package com.schoolbus.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateBookingRequest(

        @NotBlank(message = "tripNumber must not be blank")
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                        + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "tripNumber must be a valid UUID"
        )
        String tripNumber,

        @NotBlank(message = "seatNumber must not be blank")
        @Pattern(
                regexp = "^[A-Za-z0-9-]{1,10}$",
                message = "seatNumber must contain 1 to 10 letters, digits or hyphens"
        )
        String seatNumber
) {
}
