package com.schoolbus.bookingservice.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.application.booking.BookingExpirationMessage;
import com.schoolbus.bookingservice.application.booking.BookingPaymentDeadlineEvent;
import com.schoolbus.bookingservice.support.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.bookingservice.support.payment.infrastructure.outbox.ClaimedOutboxEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Profile("!test")
public class RabbitBookingExpirationEventPublisher
        implements BookingExpirationEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final BookingExpirationMessagingProperties properties;
    private final OutboxRelayProperties relayProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RabbitBookingExpirationEventPublisher(
            RabbitTemplate rabbitTemplate,
            BookingExpirationMessagingProperties properties,
            OutboxRelayProperties relayProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.rabbitTemplate = Objects.requireNonNull(
                rabbitTemplate,
                "rabbitTemplate must not be null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null"
        );
        this.relayProperties = Objects.requireNonNull(
                relayProperties,
                "relayProperties must not be null"
        );
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void publish(ClaimedOutboxEvent event) {
        ClaimedOutboxEvent checked = validateEvent(event);
        CorrelationData correlation = new CorrelationData(
                checked.eventId()
        );
        rabbitTemplate.send(
                properties.delayExchange(),
                properties.delayRoutingKey(),
                toMessage(checked),
                correlation
        );
        CorrelationData.Confirm confirm = waitForConfirm(
                checked,
                correlation
        );
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new BookingExpirationPublishException(
                    "booking expiration event was returned by RabbitMQ: "
                            + returned.getReplyText()
            );
        }
        if (!confirm.isAck()) {
            throw new BookingExpirationPublishException(
                    "RabbitMQ rejected booking expiration event: "
                            + confirm.getReason()
            );
        }
    }

    private ClaimedOutboxEvent validateEvent(ClaimedOutboxEvent event) {
        ClaimedOutboxEvent checked = Objects.requireNonNull(
                event,
                "event must not be null"
        );
        if (!BookingPaymentDeadlineEvent.TYPE.equals(checked.eventType())) {
            throw new IllegalArgumentException(
                    "unsupported booking event type: " + checked.eventType()
            );
        }
        return checked;
    }

    private Message toMessage(ClaimedOutboxEvent event) {
        BookingExpirationMessage payload = deserialize(event.payload());
        long delayMillis = Math.max(
                1L,
                Duration.between(clock.instant(), payload.expiresAt())
                        .toMillis()
        );
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setMessageId(event.eventId());
        messageProperties.setType(event.eventType());
        messageProperties.setContentType(
                MessageProperties.CONTENT_TYPE_JSON
        );
        messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
        messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        messageProperties.setTimestamp(Date.from(event.occurredAt()));
        messageProperties.setExpiration(Long.toString(delayMillis));
        messageProperties.setHeader("eventId", event.eventId());
        messageProperties.setHeader("eventType", event.eventType());
        messageProperties.setHeader(
                "occurredAt",
                event.occurredAt().toString()
        );
        if (event.traceId() != null) {
            messageProperties.setHeader("traceId", event.traceId());
        }
        return new Message(
                event.payload().getBytes(StandardCharsets.UTF_8),
                messageProperties
        );
    }

    private BookingExpirationMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    BookingExpirationMessage.class
            );
        } catch (JsonProcessingException exception) {
            throw new BookingExpirationPublishException(
                    "invalid booking expiration event payload",
                    exception
            );
        }
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
            throw new BookingExpirationPublishException(
                    "interrupted while confirming event " + event.eventId(),
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new BookingExpirationPublishException(
                    "failed to confirm event " + event.eventId(),
                    exception
            );
        }
    }
}
