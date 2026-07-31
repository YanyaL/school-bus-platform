package com.schoolbus.iam.domain.account;

public record PasswordHash(String value) {

    private static final int MAX_LENGTH = 255;

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                "passwordHash must not be blank"
            );
        }

        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "passwordHash is too long"
            );
        }

        if (!value.startsWith("{")) {
            throw new IllegalArgumentException(
                "passwordHash must contain an algorithm prefix"
            );
        }
    }

    public static PasswordHash of(String value) {
        return new PasswordHash(value);
    }
}
