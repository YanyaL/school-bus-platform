package com.schoolbus.transport.api.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancelTripRequest(

        @NotNull(message = "version must not be null")
        @Min(value = 0, message = "version must not be negative")
        Long version
) {
}
