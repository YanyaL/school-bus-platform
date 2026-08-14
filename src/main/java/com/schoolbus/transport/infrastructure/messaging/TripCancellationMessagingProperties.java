package com.schoolbus.transport.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.messaging.trip-cancellation")
public record TripCancellationMessagingProperties(
        String exchange,
        String requestedRoutingKey,
        String requestedQueue,
        String settledRoutingKey,
        String settledQueue,
        String deadLetterExchange,
        String deadLetterRoutingKey,
        String deadLetterQueue
) {
    public TripCancellationMessagingProperties {
        exchange = requireText(exchange, "exchange");
        requestedRoutingKey = requireText(requestedRoutingKey, "requestedRoutingKey");
        requestedQueue = requireText(requestedQueue, "requestedQueue");
        settledRoutingKey = requireText(settledRoutingKey, "settledRoutingKey");
        settledQueue = requireText(settledQueue, "settledQueue");
        deadLetterExchange = requireText(deadLetterExchange, "deadLetterExchange");
        deadLetterRoutingKey = requireText(deadLetterRoutingKey, "deadLetterRoutingKey");
        deadLetterQueue = requireText(deadLetterQueue, "deadLetterQueue");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
