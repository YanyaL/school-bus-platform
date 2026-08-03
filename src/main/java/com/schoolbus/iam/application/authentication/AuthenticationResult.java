package com.schoolbus.iam.application.authentication;

import com.schoolbus.iam.domain.account.Account;

import java.util.Objects;

public record AuthenticationResult(
        Account account,
        AccessToken accessToken,
        RefreshToken refreshToken
) {

    public AuthenticationResult {
        Objects.requireNonNull(
                account,
                "account must not be null"
        );
        Objects.requireNonNull(
                accessToken,
                "accessToken must not be null"
        );
        Objects.requireNonNull(
                refreshToken,
                "refreshToken must not be null"
        );
    }
}
