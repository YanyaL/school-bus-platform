package com.schoolbus.iam.api.authentication;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken must not be blank")
        String refreshToken
) {
}
