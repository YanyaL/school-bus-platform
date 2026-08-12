package com.schoolbus.transport.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateRouteStatusRequest(

        @NotBlank(message = "status must not be blank")
        String status,

        @NotNull(message = "version must not be null")
        Long version
) {
}
