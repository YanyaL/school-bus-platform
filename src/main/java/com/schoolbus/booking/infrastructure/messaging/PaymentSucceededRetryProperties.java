package com.schoolbus.booking.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(
        prefix = "school-bus.messaging.payment-succeeded-retry"
)
public record PaymentSucceededRetryProperties(
        String exchange,
        String routingKey,
        String queue,
        Duration delay,
        int maximumRetries,
        Duration confirmTimeout
) {

    public PaymentSucceededRetryProperties {
        exchange = requireText(exchange, "exchange");
        routingKey = requireText(routingKey, "routingKey");
        queue = requireText(queue, "queue");
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
                    "payment succeeded retry " + name
                            + " must not be blank"
            );
        }
        return value.strip();
    }

    private static Duration requirePositive(
            Duration value,
            String name
    ) {
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
