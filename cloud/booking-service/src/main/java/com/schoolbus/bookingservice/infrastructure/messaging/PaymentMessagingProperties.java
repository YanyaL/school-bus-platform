package com.schoolbus.bookingservice.infrastructure.messaging;

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
        String refundedRoutingKey,
        String refundedQueue,
        String refundedDeadLetterRoutingKey,
        String refundedDeadLetterQueue
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
                "payment.refunded",
                "schoolbus.booking.payment-refunded",
                "payment.refunded.dead",
                "schoolbus.booking.payment-refunded.dlq"
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
                refundedRoutingKey == null
                        ? "payment.refunded"
                        : refundedRoutingKey,
                "refundedRoutingKey"
        );
        refundedQueue = requireText(
                refundedQueue == null
                        ? "schoolbus.booking.payment-refunded"
                        : refundedQueue,
                "refundedQueue"
        );
        refundedDeadLetterRoutingKey = requireText(
                refundedDeadLetterRoutingKey == null
                        ? "payment.refunded.dead"
                        : refundedDeadLetterRoutingKey,
                "refundedDeadLetterRoutingKey"
        );
        refundedDeadLetterQueue = requireText(
                refundedDeadLetterQueue == null
                        ? "schoolbus.booking.payment-refunded.dlq"
                        : refundedDeadLetterQueue,
                "refundedDeadLetterQueue"
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
