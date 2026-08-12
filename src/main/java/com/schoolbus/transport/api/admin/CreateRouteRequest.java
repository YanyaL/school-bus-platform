package com.schoolbus.transport.api.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRouteRequest(

        @NotBlank(message = "routeCode must not be blank")
        @Size(
                max = 32,
                message = "routeCode must not exceed 32 characters"
        )
        String routeCode,

        @NotBlank(message = "departureCampus must not be blank")
        String departureCampus,

        @NotBlank(message = "arrivalCampus must not be blank")
        String arrivalCampus,

        @NotNull(message = "estimatedDurationMinutes must not be null")
        @Min(value = 1, message = "estimatedDurationMinutes must be at least 1")
        Integer estimatedDurationMinutes
) {
}
