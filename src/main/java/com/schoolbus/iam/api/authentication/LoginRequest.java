package com.schoolbus.iam.api.authentication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(
                message = "studentNumber must not be blank"
        )
        @Size(
                max = 32,
                message = "studentNumber must not exceed 32 characters"
        )
        String studentNumber,

        @NotBlank(
                message = "password must not be blank"
        )
        @Size(
                max = 72,
                message = "password must not exceed 72 characters"
        )
        String password
) {
}
