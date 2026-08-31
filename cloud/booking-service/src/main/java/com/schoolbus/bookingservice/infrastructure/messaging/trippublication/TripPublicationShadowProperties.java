package com.schoolbus.bookingservice.infrastructure.messaging.trippublication;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("school-bus.booking.trip-publication-shadow")
public record TripPublicationShadowProperties(
        @DefaultValue("schoolbus.transport.publication.events") String exchange,
        @DefaultValue("trip.published.v1") String routingKey,
        @DefaultValue("schoolbus.booking.trip-published.shadow") String queue,
        @DefaultValue("schoolbus.booking.trip-published.shadow.dlx") String deadLetterExchange,
        @DefaultValue("schoolbus.booking.trip-published.shadow.dlq") String deadLetterQueue,
        @DefaultValue("3") int maximumAttempts,
        @DefaultValue("500") long retryDelayMs) {
    public TripPublicationShadowProperties {
        for (String name : new String[]{exchange, routingKey, queue, deadLetterExchange, deadLetterQueue}) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("topology names must not be blank");
        }
        if (queue.equals("schoolbus.transport.trip-published.shadow") || queue.equals(deadLetterQueue)) {
            throw new IllegalArgumentException("use an independent Booking observation queue");
        }
        if (maximumAttempts < 1 || maximumAttempts > 5 || retryDelayMs < 1 || retryDelayMs > 5000) {
            throw new IllegalArgumentException("shadow retry budget must be bounded");
        }
    }
}
