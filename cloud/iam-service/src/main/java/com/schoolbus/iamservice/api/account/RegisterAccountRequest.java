package com.schoolbus.iamservice.api.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterAccountRequest(

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
                min = 8,
                max = 72,
                message = "password length must be between 8 and 72"
        )
        String password
) {
}
