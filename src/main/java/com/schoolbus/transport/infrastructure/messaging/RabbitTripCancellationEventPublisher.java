package com.schoolbus.transport.infrastructure.messaging;

import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingsSettledEvent;
import com.schoolbus.payment.infrastructure.messaging.OutboxPublishException;
import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;
import com.schoolbus.transport.application.trip.TripCancellationRequestedEvent;
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
public class RabbitTripCancellationEventPublisher
        implements TripCancellationEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final TripCancellationMessagingProperties messagingProperties;
    private final OutboxRelayProperties relayProperties;

    public RabbitTripCancellationEventPublisher(
            RabbitTemplate rabbitTemplate,
            TripCancellationMessagingProperties messagingProperties,
            OutboxRelayProperties relayProperties
    ) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate);
        this.messagingProperties = Objects.requireNonNull(messagingProperties);
        this.relayProperties = Objects.requireNonNull(relayProperties);
    }

    @Override
    public void publish(ClaimedOutboxEvent event) {
        ClaimedOutboxEvent checked = Objects.requireNonNull(event);
        CorrelationData correlation = new CorrelationData(checked.eventId());
        rabbitTemplate.send(
                messagingProperties.exchange(),
                routingKey(checked.eventType()),
                toMessage(checked),
                correlation
        );
        CorrelationData.Confirm confirm = waitForConfirm(checked, correlation);
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new OutboxPublishException(
                    "trip cancellation event was returned: "
                            + returned.getReplyText()
            );
        }
        if (!confirm.isAck()) {
            throw new OutboxPublishException(
                    "RabbitMQ rejected trip cancellation event: "
                            + confirm.getReason()
            );
        }
    }

    private String routingKey(String eventType) {
        return switch (eventType) {
            case TripCancellationRequestedEvent.TYPE ->
                    messagingProperties.requestedRoutingKey();
            case TripCancellationBookingsSettledEvent.TYPE ->
                    messagingProperties.settledRoutingKey();
            default -> throw new IllegalArgumentException(
                    "unsupported trip cancellation event type: " + eventType
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
        MessageProperties p = new MessageProperties();
        p.setMessageId(event.eventId());
        p.setType(event.eventType());
        p.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        p.setContentEncoding(StandardCharsets.UTF_8.name());
        p.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        p.setTimestamp(Date.from(event.occurredAt()));
        p.setHeader("eventId", event.eventId());
        p.setHeader("eventType", event.eventType());
        if (event.traceId() != null) {
            p.setHeader("traceId", event.traceId());
        }
        return new Message(
                event.payload().getBytes(StandardCharsets.UTF_8),
                p
        );
    }
}
