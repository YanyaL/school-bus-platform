package com.schoolbus.paymentservice.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.messaging.payment")
public record PaymentMessagingProperties(
        String exchange,
        String refundRoutingKey,
        String refundQueue,
        String deadLetterExchange,
        String deadLetterRoutingKey,
        String deadLetterQueue
) {

    public PaymentMessagingProperties {
        exchange = requireText(exchange, "exchange");
        refundRoutingKey = requireText(
                refundRoutingKey,
                "refundRoutingKey"
        );
        refundQueue = requireText(refundQueue, "refundQueue");
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
                    "payment messaging " + name + " must not be blank"
            );
        }
        return value.strip();
    }
}
