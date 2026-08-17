package com.schoolbus.iamservice.api.authentication;

import com.schoolbus.iamservice.application.authentication.AuthenticationResult;
import com.schoolbus.iamservice.domain.account.Account;
import com.schoolbus.iamservice.api.HttpResourceId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record LoginResponse(
        String userId,
        String studentNumber,
        List<String> roles,
        String tokenType,
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    public static LoginResponse from(AuthenticationResult result) {
        AuthenticationResult validatedResult = Objects.requireNonNull(
                result,
                "result must not be null"
        );
        Account account = validatedResult.account();

        List<String> roleNames = account.roles()
                .stream()
                .map(Enum::name)
                .sorted()
                .toList();

        return new LoginResponse(
                HttpResourceId.format(account.userId().value()),
                account.studentNumber().value(),
                roleNames,
                validatedResult.accessToken().tokenType(),
                validatedResult.accessToken().value(),
                validatedResult.accessToken().expiresAt(),
                validatedResult.refreshToken().value(),
                validatedResult.refreshToken().expiresAt()
        );
    }
}
