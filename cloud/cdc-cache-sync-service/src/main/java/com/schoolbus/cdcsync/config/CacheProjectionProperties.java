package com.schoolbus.cdcsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "school-bus.cdc.cache")
public record CacheProjectionProperties(
        String tripListKey,
        String consumedEventKeyPrefix,
        Duration consumedEventTtl
) {

    public CacheProjectionProperties {
        tripListKey = requireText(tripListKey, "tripListKey");
        consumedEventKeyPrefix = requireText(
                consumedEventKeyPrefix,
                "consumedEventKeyPrefix"
        );
        if (consumedEventTtl == null
                || consumedEventTtl.isZero()
                || consumedEventTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "consumedEventTtl must be positive"
            );
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
