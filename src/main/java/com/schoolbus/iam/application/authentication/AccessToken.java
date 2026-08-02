package com.schoolbus.iam.application.authentication;

import java.time.Instant;
import java.util.Objects;

public record AccessToken(
        String value,
        String tokenType,
        Instant issuedAt,
        Instant expiresAt
) {

    public static final String BEARER_TYPE = "Bearer";

    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "value must not be blank"
            );
        }
        if (tokenType == null || tokenType.isBlank()) {
            throw new IllegalArgumentException(
                    "tokenType must not be blank"
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
