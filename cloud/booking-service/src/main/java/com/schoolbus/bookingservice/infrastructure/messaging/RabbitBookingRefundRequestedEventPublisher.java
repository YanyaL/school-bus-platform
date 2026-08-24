package com.schoolbus.bookingservice.infrastructure.messaging;

import com.schoolbus.bookingservice.support.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.bookingservice.support.payment.infrastructure.outbox.ClaimedOutboxEvent;
import com.schoolbus.bookingservice.support.payment.infrastructure.outbox.MyBatisPaymentRefundOutbox;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Profile("!test")
public class RabbitBookingRefundRequestedEventPublisher
        implements BookingRefundRequestedEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final PaymentMessagingProperties messagingProperties;
    private final OutboxRelayProperties relayProperties;

    public RabbitBookingRefundRequestedEventPublisher(
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
        if (!MyBatisPaymentRefundOutbox.EVENT_TYPE.equals(
                checked.eventType()
        )) {
            throw new IllegalArgumentException(
                    "unsupported booking refund event type: "
                            + checked.eventType()
            );
        }
        CorrelationData correlation = new CorrelationData(checked.eventId());
        rabbitTemplate.send(
                messagingProperties.exchange(),
                messagingProperties.refundRoutingKey(),
                toMessage(checked),
                correlation
        );
        CorrelationData.Confirm confirm = waitForConfirm(
                checked,
                correlation
        );
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new BookingRefundRequestedPublishException(
                    "RefundRequested event was returned by RabbitMQ: "
                            + returned.getReplyText()
            );
        }
        if (!confirm.isAck()) {
            throw new BookingRefundRequestedPublishException(
                    "RabbitMQ rejected RefundRequested event: "
                            + confirm.getReason()
            );
        }
    }

    private Message toMessage(ClaimedOutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(event.eventId());
        properties.setType(MyBatisPaymentRefundOutbox.EVENT_TYPE);
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setTimestamp(Date.from(event.occurredAt()));
        properties.setHeader("eventId", event.eventId());
        properties.setHeader(
                "eventType",
                MyBatisPaymentRefundOutbox.EVENT_TYPE
        );
        properties.setHeader("occurredAt", event.occurredAt().toString());
        if (event.traceId() != null) {
            properties.setHeader("traceId", event.traceId());
        }
        return new Message(
                event.payload().getBytes(StandardCharsets.UTF_8),
                properties
        );
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
            throw new BookingRefundRequestedPublishException(
                    "interrupted while confirming event " + event.eventId(),
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new BookingRefundRequestedPublishException(
                    "failed to confirm event " + event.eventId(),
                    exception
            );
        }
    }
}
