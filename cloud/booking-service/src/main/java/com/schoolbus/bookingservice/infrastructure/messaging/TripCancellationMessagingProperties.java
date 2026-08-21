package com.schoolbus.bookingservice.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.messaging.trip-cancellation")
public record TripCancellationMessagingProperties(
        String exchange,
        String requestedRoutingKey,
        String requestedQueue,
        String deadLetterExchange,
        String deadLetterRoutingKey,
        String deadLetterQueue
) {
    public TripCancellationMessagingProperties {
        exchange = requireText(exchange, "exchange");
        requestedRoutingKey = requireText(requestedRoutingKey, "requestedRoutingKey");
        requestedQueue = requireText(requestedQueue, "requestedQueue");
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
