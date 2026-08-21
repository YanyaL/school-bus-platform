package com.schoolbus.cdcsync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "school-bus.cdc.messaging")
public record CdcMessagingProperties(
        String exchange,
        String tripRoutingKey,
        String tripQueue,
        String consumedEventRoutingKey,
        String consumedEventQueue,
        Duration confirmTimeout
) {

    public CdcMessagingProperties {
        exchange = requireText(exchange, "exchange");
        tripRoutingKey = requireText(tripRoutingKey, "tripRoutingKey");
        tripQueue = requireText(tripQueue, "tripQueue");
        consumedEventRoutingKey = requireText(
                consumedEventRoutingKey,
                "consumedEventRoutingKey"
        );
        consumedEventQueue = requireText(
                consumedEventQueue,
                "consumedEventQueue"
        );
        if (confirmTimeout == null
                || confirmTimeout.isZero()
                || confirmTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "confirmTimeout must be positive"
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
