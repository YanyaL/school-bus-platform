package com.schoolbus.transport.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.transport.application.trip.TripCancellationCompletionTransaction;
import com.schoolbus.transport.application.trip.TripCancellationSettledEnvelope;
import com.schoolbus.transport.application.trip.TripCancellationSettledMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
@Profile("local")
public class TripCancellationSettledListener {

    private static final Logger log = LoggerFactory.getLogger(
            TripCancellationSettledListener.class
    );

    private final TripCancellationCompletionTransaction transaction;
    private final ObjectMapper objectMapper;
    private final TripCancellationRetryPublisher retryPublisher;
    private final TripCancellationRetryAttemptResolver attemptResolver;
    private final TripCancellationRetryProperties retryProperties;

    public TripCancellationSettledListener(
            TripCancellationCompletionTransaction transaction,
            ObjectMapper objectMapper,
            TripCancellationRetryPublisher retryPublisher,
            TripCancellationRetryAttemptResolver attemptResolver,
            TripCancellationRetryProperties retryProperties
    ) {
        this.transaction = Objects.requireNonNull(transaction);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.retryPublisher = Objects.requireNonNull(retryPublisher);
        this.attemptResolver = Objects.requireNonNull(attemptResolver);
        this.retryProperties = Objects.requireNonNull(retryProperties);
    }

    @RabbitListener(
            queues = "${school-bus.messaging.trip-cancellation.settled-queue}"
    )
    public void consume(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            String eventId = message.getMessageProperties().getMessageId();
            TripCancellationSettledMessage payload = objectMapper.readValue(
                    message.getBody(),
                    TripCancellationSettledMessage.class
            );
            boolean changed = transaction.complete(
                    new TripCancellationSettledEnvelope(eventId, payload)
            );
            channel.basicAck(tag, false);
            log.info(
                    "Trip cancellation finalized: tripId={}, changed={}",
                    payload.tripId(),
                    changed
            );
        } catch (IOException | IllegalArgumentException exception) {
            log.error("Malformed trip cancellation settled event", exception);
            channel.basicReject(tag, false);
        } catch (RuntimeException exception) {
            scheduleRetryOrReject(message, channel, tag, exception);
        }
    }

    private void scheduleRetryOrReject(
            Message message,
            Channel channel,
            long tag,
            RuntimeException processingFailure
    ) throws IOException {
        int completedRetries = attemptResolver.completedRetries(
                message,
                retryProperties.settledQueue()
        );
        if (completedRetries >= retryProperties.maximumRetries()) {
            log.error(
                    "Trip cancellation settled event exhausted {} retries",
                    retryProperties.maximumRetries(),
                    processingFailure
            );
            channel.basicReject(tag, false);
            return;
        }
        try {
            retryPublisher.scheduleRetry(
                    message,
                    TripCancellationRetryLane.SETTLED
            );
            channel.basicAck(tag, false);
            log.warn(
                    "Trip cancellation settled event scheduled for retry {}/{} after {}",
                    completedRetries + 1,
                    retryProperties.maximumRetries(),
                    retryProperties.delay(),
                    processingFailure
            );
        } catch (RuntimeException publishFailure) {
            processingFailure.addSuppressed(publishFailure);
            log.error(
                    "Failed to publish cancellation settled retry; original will be requeued",
                    processingFailure
            );
            channel.basicNack(tag, false, true);
        }
    }
}
