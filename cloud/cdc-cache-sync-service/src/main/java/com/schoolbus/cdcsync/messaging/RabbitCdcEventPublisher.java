package com.schoolbus.cdcsync.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.cdcsync.config.CdcMessagingProperties;
import com.schoolbus.cdcsync.event.CdcEvent;
import com.schoolbus.cdcsync.event.ConsumedEventRecordedEvent;
import com.schoolbus.cdcsync.event.TripCacheInvalidationEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class RabbitCdcEventPublisher implements CdcEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final CdcMessagingProperties properties;

    public RabbitCdcEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            CdcMessagingProperties properties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void publish(CdcEvent event) {
        String routingKey = routingKey(event);
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbitTemplate.send(
                properties.exchange(),
                routingKey,
                toMessage(event),
                correlation
        );

        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    properties.confirmTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!confirm.isAck()) {
                throw new IllegalStateException(
                        "RabbitMQ rejected CDC event " + event.eventId()
                                + ": " + confirm.getReason()
                );
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException(
                        "RabbitMQ returned unroutable CDC event "
                                + event.eventId()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted while confirming CDC event " + event.eventId(),
                    exception
            );
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException(
                    "cannot confirm CDC event " + event.eventId(),
                    exception
            );
        }
    }

    private Message toMessage(CdcEvent event) {
        try {
            return MessageBuilder
                    .withBody(objectMapper.writeValueAsBytes(event))
                    .setContentType("application/json")
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(event.eventId())
                    .setType(event.getClass().getSimpleName())
                    .build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "cannot serialize CDC event " + event.eventId(),
                    exception
            );
        }
    }

    private String routingKey(CdcEvent event) {
        return switch (event) {
            case TripCacheInvalidationEvent ignored ->
                    properties.tripRoutingKey();
            case ConsumedEventRecordedEvent ignored ->
                    properties.consumedEventRoutingKey();
        };
    }
}
