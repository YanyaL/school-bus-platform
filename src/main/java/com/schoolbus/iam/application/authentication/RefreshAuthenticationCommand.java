package com.schoolbus.iam.application.authentication;

public record RefreshAuthenticationCommand(
        String rawRefreshToken
) {

    public RefreshAuthenticationCommand {
        if (rawRefreshToken == null
                || rawRefreshToken.isBlank()) {
            throw new IllegalArgumentException(
                    "rawRefreshToken must not be blank"
            );
        }
    }
}
