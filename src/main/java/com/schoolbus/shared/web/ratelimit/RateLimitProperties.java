package com.schoolbus.shared.web.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        double loginQps,
        double createBookingQps,
        double paymentCallbackQps
) {

    public RateLimitProperties {
        requirePositive(loginQps, "loginQps");
        requirePositive(createBookingQps, "createBookingQps");
        requirePositive(paymentCallbackQps, "paymentCallbackQps");
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0D) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
