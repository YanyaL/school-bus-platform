package com.schoolbus.iam.api.authentication;

import com.schoolbus.iam.application.authentication.AuthenticationResult;
import com.schoolbus.iam.domain.account.Account;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record LoginResponse(
        long userId,
        String studentNumber,
        List<String> roles,
        String tokenType,
        String accessToken,
        Instant expiresAt
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
                account.userId().value(),
                account.studentNumber().value(),
                roleNames,
                validatedResult.accessToken().tokenType(),
                validatedResult.accessToken().value(),
                validatedResult.accessToken().expiresAt()
        );
    }
}
