package com.schoolbus.iam.api.authentication;

import com.schoolbus.iam.application.authentication.AuthenticationResult;

import java.time.Instant;
import java.util.Objects;

public record RefreshTokenResponse(
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    public static RefreshTokenResponse from(
            AuthenticationResult result
    ) {
        AuthenticationResult validatedResult = Objects.requireNonNull(
                result,
                "result must not be null"
        );

        return new RefreshTokenResponse(
                validatedResult.accessToken().tokenType(),
                validatedResult.accessToken().value(),
                validatedResult.accessToken().expiresAt(),
                validatedResult.refreshToken().value(),
                validatedResult.refreshToken().expiresAt()
        );
    }
}
