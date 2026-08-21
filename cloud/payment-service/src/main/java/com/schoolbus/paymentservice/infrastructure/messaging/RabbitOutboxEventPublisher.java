package com.schoolbus.paymentservice.infrastructure.messaging;

import com.schoolbus.paymentservice.infrastructure.outbox.ClaimedOutboxEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class RabbitOutboxEventPublisher implements OutboxEventPublisher {

    private static final String PAYMENT_REFUND_REQUIRED =
            "PaymentRefundRequired";
    private static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";

    private final RabbitTemplate rabbitTemplate;
    private final PaymentMessagingProperties messagingProperties;
    private final OutboxRelayProperties relayProperties;

    public RabbitOutboxEventPublisher(
            RabbitTemplate rabbitTemplate,
            PaymentMessagingProperties messagingProperties,
            OutboxRelayProperties relayProperties
    ) {
        this.rabbitTemplate = Objects.requireNonNull(
                rabbitTemplate,
                "rabbitTemplate must not be null"
        );
        this.messagingProperties = Objects.requireNonNull(
                messagingProperties,
                "messagingProperties must not be null"
        );
        this.relayProperties = Objects.requireNonNull(
                relayProperties,
                "relayProperties must not be null"
        );
    }

    @Override
    public void publish(ClaimedOutboxEvent event) {
        ClaimedOutboxEvent checked = Objects.requireNonNull(
                event,
                "event must not be null"
        );
        CorrelationData correlation = new CorrelationData(
                checked.eventId()
        );
        rabbitTemplate.send(
                messagingProperties.exchange(),
                routingKey(checked),
                toMessage(checked),
                correlation
        );
        CorrelationData.Confirm confirm = waitForConfirm(
                checked,
                correlation
        );
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new OutboxPublishException(
                    "payment event was returned by RabbitMQ: "
                            + returned.getReplyText()
            );
        }
        if (!confirm.isAck()) {
            throw new OutboxPublishException(
                    "RabbitMQ rejected payment event: "
                            + confirm.getReason()
            );
        }
    }

    private String routingKey(ClaimedOutboxEvent event) {
        return switch (event.eventType()) {
            case PAYMENT_REFUND_REQUIRED ->
                    messagingProperties.refundRoutingKey();
            case PAYMENT_SUCCEEDED ->
                    messagingProperties.succeededRoutingKey();
            default -> throw new OutboxPublishException(
                    "unsupported payment event type: " + event.eventType()
            );
        };
    }

    private CorrelationData.Confirm waitForConfirm(
            ClaimedOutboxEvent event,
            CorrelationData correlation
    ) {
        try {
            return correlation.getFuture().get(
                    relayProperties.confirmTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException(
                    "interrupted while confirming event " + event.eventId(),
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new OutboxPublishException(
                    "failed to confirm event " + event.eventId(),
                    exception
            );
        }
    }

    private Message toMessage(ClaimedOutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.eventId());
        properties.setType(event.eventType());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setTimestamp(Date.from(event.occurredAt()));
        properties.setHeader("eventId", event.eventId());
        properties.setHeader("eventType", event.eventType());
        properties.setHeader("occurredAt", event.occurredAt().toString());
        if (event.traceId() != null) {
            properties.setHeader("traceId", event.traceId());
        }
        return new Message(
                event.payload().getBytes(StandardCharsets.UTF_8),
                properties
        );
    }
}
