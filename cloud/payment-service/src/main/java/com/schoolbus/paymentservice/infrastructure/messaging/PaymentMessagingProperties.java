package com.schoolbus.paymentservice.infrastructure.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "school-bus.messaging.payment")
public record PaymentMessagingProperties(
        String exchange,
        String refundRoutingKey,
        String refundQueue,
        String deadLetterExchange,
        String deadLetterRoutingKey,
        String deadLetterQueue,
        String succeededRoutingKey,
        String succeededQueue,
        String succeededDeadLetterRoutingKey,
        String succeededDeadLetterQueue,
        String refundedRoutingKey
) {

    public PaymentMessagingProperties(
            String exchange,
            String refundRoutingKey,
            String refundQueue,
            String deadLetterExchange,
            String deadLetterRoutingKey,
            String deadLetterQueue
    ) {
        this(
                exchange,
                refundRoutingKey,
                refundQueue,
                deadLetterExchange,
                deadLetterRoutingKey,
                deadLetterQueue,
                "payment.succeeded",
                "schoolbus.booking.payment-succeeded",
                "payment.succeeded.dead",
                "schoolbus.booking.payment-succeeded.dlq",
                "payment.refunded"
        );
    }

    @org.springframework.boot.context.properties.bind.ConstructorBinding
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
        succeededRoutingKey = requireText(
                succeededRoutingKey,
                "succeededRoutingKey"
        );
        succeededQueue = requireText(
                succeededQueue,
                "succeededQueue"
        );
        succeededDeadLetterRoutingKey = requireText(
                succeededDeadLetterRoutingKey,
                "succeededDeadLetterRoutingKey"
        );
        succeededDeadLetterQueue = requireText(
                succeededDeadLetterQueue,
                "succeededDeadLetterQueue"
        );
        refundedRoutingKey = requireText(
                refundedRoutingKey == null ? "payment.refunded" : refundedRoutingKey,
                "refundedRoutingKey"
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
