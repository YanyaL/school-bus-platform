package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.shared.api.BusinessException;
import com.schoolbus.bookingservice.shared.api.ErrorCode;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BookingSortOption {

    private static final Pattern PATTERN = Pattern.compile(
            "^createdAt,(asc|desc)$",
            Pattern.CASE_INSENSITIVE
    );

    private BookingSortOption() {
    }

    public static boolean parseCreatedAtAscending(String sort) {
        Matcher matcher = PATTERN.matcher(
                sort == null ? "" : sort.strip()
        );
        if (!matcher.matches()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "sort must be createdAt,asc or createdAt,desc"
            );
        }
        return "asc".equals(matcher.group(1).toLowerCase(Locale.ROOT));
    }
}
