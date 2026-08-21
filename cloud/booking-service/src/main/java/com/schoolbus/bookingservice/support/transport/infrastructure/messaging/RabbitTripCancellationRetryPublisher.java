package com.schoolbus.bookingservice.support.transport.infrastructure.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Profile("!test")
public class RabbitTripCancellationRetryPublisher
        implements TripCancellationRetryPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final TripCancellationRetryProperties properties;

    public RabbitTripCancellationRetryPublisher(
            RabbitTemplate rabbitTemplate,
            TripCancellationRetryProperties properties
    ) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public void scheduleRetry(
            Message message
    ) {
        Message checked = Objects.requireNonNull(message);
        Message retryMessage = MessageBuilder
                .fromClonedMessage(checked)
                .build();
        CorrelationData correlation = new CorrelationData(
                correlationId(checked)
        );
        rabbitTemplate.send(
                properties.exchange(),
                properties.requestedRoutingKey(),
                retryMessage,
                correlation
        );
        CorrelationData.Confirm confirm = waitForConfirm(correlation);
        ReturnedMessage returned = correlation.getReturned();
        if (returned != null) {
            throw new TripCancellationRetryPublishException(
                    "trip cancellation retry message was returned: "
                            + returned.getReplyText()
            );
        }
        if (!confirm.isAck()) {
            throw new TripCancellationRetryPublishException(
                    "RabbitMQ rejected trip cancellation retry message: "
                            + confirm.getReason()
            );
        }
    }

    private CorrelationData.Confirm waitForConfirm(
            CorrelationData correlation
    ) {
        try {
            return correlation.getFuture().get(
                    properties.confirmTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TripCancellationRetryPublishException(
                    "interrupted while confirming cancellation retry",
                    exception
            );
        } catch (ExecutionException | TimeoutException exception) {
            throw new TripCancellationRetryPublishException(
                    "failed to confirm cancellation retry",
                    exception
            );
        }
    }

    private String correlationId(
            Message message
    ) {
        String messageId = message.getMessageProperties().getMessageId();
        String prefix = messageId == null ? "trip-cancellation" : messageId;
        return prefix + ":requested:retry:" + UUID.randomUUID();
    }
}
