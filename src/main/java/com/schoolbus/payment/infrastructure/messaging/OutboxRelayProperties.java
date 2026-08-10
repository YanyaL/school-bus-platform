package com.schoolbus.payment.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "school-bus.messaging.outbox-relay")
public record OutboxRelayProperties(
        boolean enabled,
        int batchSize,
        Duration claimTimeout,
        Duration confirmTimeout,
        Duration initialRetryDelay,
        Duration maximumRetryDelay,
        int maximumAttempts
) {

    public OutboxRelayProperties {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        claimTimeout = requirePositive(claimTimeout, "claimTimeout");
        confirmTimeout = requirePositive(confirmTimeout, "confirmTimeout");
        initialRetryDelay = requirePositive(
                initialRetryDelay,
                "initialRetryDelay"
        );
        maximumRetryDelay = requirePositive(
                maximumRetryDelay,
                "maximumRetryDelay"
        );
        if (maximumRetryDelay.compareTo(initialRetryDelay) < 0) {
            throw new IllegalArgumentException(
                    "maximumRetryDelay must not be shorter than initialRetryDelay"
            );
        }
        if (maximumAttempts <= 0) {
            throw new IllegalArgumentException(
                    "maximumAttempts must be positive"
            );
        }
    }

    private static Duration requirePositive(
            Duration duration,
            String name
    ) {
        Duration checked = Objects.requireNonNull(
                duration,
                name + " must not be null"
        );
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }
}
