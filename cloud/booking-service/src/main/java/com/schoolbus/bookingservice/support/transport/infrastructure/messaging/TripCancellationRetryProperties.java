package com.schoolbus.bookingservice.support.transport.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(
        prefix = "school-bus.messaging.trip-cancellation-retry"
)
public record TripCancellationRetryProperties(
        String exchange,
        String requestedRoutingKey,
        String requestedQueue,
        Duration delay,
        int maximumRetries,
        Duration confirmTimeout
) {

    public TripCancellationRetryProperties {
        exchange = requireText(exchange, "exchange");
        requestedRoutingKey = requireText(
                requestedRoutingKey,
                "requestedRoutingKey"
        );
        requestedQueue = requireText(requestedQueue, "requestedQueue");
        delay = requirePositive(delay, "delay");
        if (maximumRetries <= 0) {
            throw new IllegalArgumentException(
                    "maximumRetries must be positive"
            );
        }
        confirmTimeout = requirePositive(
                confirmTimeout,
                "confirmTimeout"
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "trip cancellation retry " + name
                            + " must not be blank"
            );
        }
        return value.strip();
    }

    private static Duration requirePositive(Duration value, String name) {
        Duration checked = Objects.requireNonNull(
                value,
                name + " must not be null"
        );
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }
}
