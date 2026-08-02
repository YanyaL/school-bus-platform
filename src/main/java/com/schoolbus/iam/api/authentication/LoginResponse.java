package com.schoolbus.iam.api.authentication;

import com.schoolbus.iam.domain.account.Account;

import java.util.List;
import java.util.Objects;

public record LoginResponse(
        long userId,
        String studentNumber,
        List<String> roles
) {

    public static LoginResponse from(Account account) {
        Account validatedAccount = Objects.requireNonNull(
                account,
                "account must not be null"
        );

        List<String> roleNames = validatedAccount.roles()
                .stream()
                .map(Enum::name)
                .sorted()
                .toList();

        return new LoginResponse(
                validatedAccount.userId().value(),
                validatedAccount.studentNumber().value(),
                roleNames
        );
    }
}
