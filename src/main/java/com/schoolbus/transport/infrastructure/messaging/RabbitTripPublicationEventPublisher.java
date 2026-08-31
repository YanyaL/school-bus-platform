package com.schoolbus.transport.infrastructure.messaging;

import com.schoolbus.payment.infrastructure.messaging.OutboxPublishException;
import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;
import com.schoolbus.transport.application.trip.TripPublishedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(prefix = "school-bus.transport.publication-events", name = "enabled", havingValue = "true")
public class RabbitTripPublicationEventPublisher implements TripPublicationEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final TripPublicationMessagingProperties topology;
    private final OutboxRelayProperties relayProperties;

    public RabbitTripPublicationEventPublisher(RabbitTemplate rabbitTemplate,
            TripPublicationMessagingProperties topology, OutboxRelayProperties relayProperties) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate);
        this.topology = Objects.requireNonNull(topology);
        this.relayProperties = Objects.requireNonNull(relayProperties);
    }

    @Override
    public void publish(ClaimedOutboxEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (!TripPublishedEvent.TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException("unsupported publication event type: " + event.eventType());
        }
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.eventId());
        properties.setType(event.eventType());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setTimestamp(Date.from(event.occurredAt()));
        properties.setHeader("eventId", event.eventId());
        properties.setHeader("eventType", event.eventType());
        properties.setHeader("schemaVersion", TripPublishedEvent.SCHEMA_VERSION);
        if (event.traceId() != null) {
            properties.setHeader("traceId", event.traceId());
        }
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbitTemplate.send(topology.exchange(), topology.routingKey(),
                new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties), correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    relayProperties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) {
                throw new OutboxPublishException("TripPublished was returned: "
                        + correlation.getReturned().getReplyText());
            }
            if (!confirm.isAck()) {
                throw new OutboxPublishException("RabbitMQ rejected TripPublished: " + confirm.getReason());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("interrupted while confirming TripPublished", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new OutboxPublishException("failed to confirm TripPublished " + event.eventId(), exception);
        }
    }
}
