package com.schoolbus.iam.domain.account;

import java.util.Locale;
import java.util.regex.Pattern;

public record StudentNumber(String value) {

    private static final Pattern FORMAT =
        Pattern.compile("[A-Z0-9]{1,32}");

    public StudentNumber {
        if (value == null) {
            throw new IllegalArgumentException(
                "studentNumber must not be null"
            );
        }

        String normalized = value
            .strip()
            .toUpperCase(Locale.ROOT);

        if (!FORMAT.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                "studentNumber format is invalid"
            );
        }

        value = normalized;
    }

    public static StudentNumber of(String value) {
        return new StudentNumber(value);
    }
}
