package com.schoolbus.transportquery.api;

import java.util.regex.Pattern;

public final class HttpResourceId {

    private static final Pattern POSITIVE_DECIMAL =
            Pattern.compile("^[1-9][0-9]{0,18}$");

    private HttpResourceId() {
    }

    public static String format(long value) {
        if (value <= 0L) {
            throw new IllegalArgumentException(
                    "resource id must be positive: " + value
            );
        }
        return Long.toString(value);
    }

    public static long parse(String value, String fieldName) {
        String name = fieldName == null || fieldName.isBlank()
                ? "id"
                : fieldName.strip();
        if (value == null) {
            throw validation(name + " must not be null");
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw validation(name + " must not be blank");
        }
        if (!POSITIVE_DECIMAL.matcher(trimmed).matches()) {
            throw validation(
                    name + " must be a positive decimal integer string"
            );
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException exception) {
            throw validation(name + " exceeds Long.MAX_VALUE");
        }
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
