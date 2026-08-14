package com.schoolbus.shared.web.ratelimit;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;

public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException(String resource) {
        super(
                ErrorCode.RATE_LIMITED,
                "too many requests for resource " + requireText(resource)
        );
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "resource must not be blank"
            );
        }
        return value.strip();
    }
}
