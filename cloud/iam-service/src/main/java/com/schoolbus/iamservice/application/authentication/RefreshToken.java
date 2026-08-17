package com.schoolbus.iamservice.application.authentication;

import java.time.Instant;
import java.util.Objects;

public record RefreshToken(
        String value,
        Instant issuedAt,
        Instant expiresAt
) {

    public RefreshToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "value must not be blank"
            );
        }
        Objects.requireNonNull(
                issuedAt,
                "issuedAt must not be null"
        );
        Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
        );
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after issuedAt"
            );
        }
    }
}
