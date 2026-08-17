package com.schoolbus.iamservice.api.account;

import com.schoolbus.iamservice.domain.account.Account;
import com.schoolbus.iamservice.api.HttpResourceId;

import java.util.List;
import java.util.Objects;

public record RegisterAccountResponse(
        String userId,
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
                HttpResourceId.format(validatedAccount.userId().value()),
                validatedAccount.studentNumber().value(),
                validatedAccount.status().name(),
                roleNames
        );
    }
}
