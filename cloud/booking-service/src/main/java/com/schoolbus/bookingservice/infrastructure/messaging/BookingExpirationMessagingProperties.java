package com.schoolbus.bookingservice.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "school-bus.messaging.booking-expiration"
)
public record BookingExpirationMessagingProperties(
        String delayExchange,
        String delayRoutingKey,
        String delayQueue,
        String processingExchange,
        String processingRoutingKey,
        String processingQueue,
        String deadLetterExchange,
        String deadLetterRoutingKey,
        String deadLetterQueue
) {

    public BookingExpirationMessagingProperties {
        delayExchange = requireText(delayExchange, "delayExchange");
        delayRoutingKey = requireText(delayRoutingKey, "delayRoutingKey");
        delayQueue = requireText(delayQueue, "delayQueue");
        processingExchange = requireText(
                processingExchange,
                "processingExchange"
        );
        processingRoutingKey = requireText(
                processingRoutingKey,
                "processingRoutingKey"
        );
        processingQueue = requireText(
                processingQueue,
                "processingQueue"
        );
        deadLetterExchange = requireText(
                deadLetterExchange,
                "deadLetterExchange"
        );
        deadLetterRoutingKey = requireText(
                deadLetterRoutingKey,
                "deadLetterRoutingKey"
        );
        deadLetterQueue = requireText(
                deadLetterQueue,
                "deadLetterQueue"
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "booking expiration messaging " + name
                            + " must not be blank"
            );
        }
        return value.strip();
    }
}
