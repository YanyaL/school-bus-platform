package com.schoolbus.iam.application.authentication;

import com.schoolbus.shared.domain.identity.UserId;

import java.time.Instant;
import java.util.Objects;

public record LoginSession(
        String sessionId,
        UserId userId,
        String refreshTokenHash,
        Instant createdAt,
        Instant expiresAt
) {

    public LoginSession {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException(
                    "sessionId must not be blank"
            );
        }
        Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        if (refreshTokenHash == null
                || refreshTokenHash.isBlank()) {
            throw new IllegalArgumentException(
                    "refreshTokenHash must not be blank"
            );
        }
        Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
        Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
        );
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after createdAt"
            );
        }
    }

    public boolean isExpiredAt(Instant instant) {
        Instant checkedInstant = Objects.requireNonNull(
                instant,
                "instant must not be null"
        );
        return !checkedInstant.isBefore(expiresAt);
    }
}
