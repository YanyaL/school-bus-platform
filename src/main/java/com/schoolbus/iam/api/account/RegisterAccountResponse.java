package com.schoolbus.iam.api.account;

import com.schoolbus.iam.domain.account.Account;

import java.util.List;
import java.util.Objects;

public record RegisterAccountResponse(
        long userId,
        String studentNumber,
        String status,
        List<String> roles
) {

    public static RegisterAccountResponse from(
            Account account
    ) {
        Account validatedAccount = Objects.requireNonNull(
                account,
                "account must not be null"
        );

        List<String> roleNames = validatedAccount.roles()
                .stream()
                .map(Enum::name)
                .sorted()
                .toList();

        return new RegisterAccountResponse(
                validatedAccount.userId().value(),
                validatedAccount.studentNumber().value(),
                validatedAccount.status().name(),
                roleNames
        );
    }
}
