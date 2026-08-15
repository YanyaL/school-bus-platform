package com.schoolbus.booking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBookingRequest(

        @NotBlank(message = "tripId must not be blank")
        @Size(max = 19, message = "tripId must not exceed 19 digits")
        @Pattern(
                regexp = "^[1-9][0-9]{0,18}$",
                message = "tripId must be a positive decimal integer string"
        )
        String tripId,

        @NotBlank(message = "seatNumber must not be blank")
        @Pattern(
                regexp = "^[A-Za-z0-9-]{1,10}$",
                message = "seatNumber must contain 1 to 10 letters, digits or hyphens"
        )
        String seatNumber
) {
}
