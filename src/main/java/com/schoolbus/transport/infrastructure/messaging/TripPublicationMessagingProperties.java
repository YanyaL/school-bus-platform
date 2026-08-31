package com.schoolbus.transport.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "school-bus.transport.publication-events")
public record TripPublicationMessagingProperties(
        @DefaultValue("schoolbus.transport.publication.events") String exchange,
        @DefaultValue("trip.published.v1") String routingKey,
        @DefaultValue("schoolbus.transport.trip-published.shadow") String shadowQueue,
        @DefaultValue("10000") long maximumQueueLength
) {
    public TripPublicationMessagingProperties {
        exchange = requireText(exchange);
        routingKey = requireText(routingKey);
        shadowQueue = requireText(shadowQueue);
        if (maximumQueueLength <= 0) {
            throw new IllegalArgumentException("maximumQueueLength must be positive");
        }
    }

    private static String requireText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("publication topology names must not be blank");
        }
        return text.strip();
    }
}
