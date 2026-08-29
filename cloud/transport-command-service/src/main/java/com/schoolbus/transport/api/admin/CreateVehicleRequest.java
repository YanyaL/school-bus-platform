package com.schoolbus.transport.api.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVehicleRequest(

        @NotBlank(message = "licensePlate must not be blank")
        @Size(
                max = 20,
                message = "licensePlate must not exceed 20 characters"
        )
        String licensePlate,

        @NotNull(message = "seatCount must not be null")
        @Min(value = 1, message = "seatCount must be at least 1")
        @Max(value = 120, message = "seatCount must not exceed 120")
        Integer seatCount
) {
}
