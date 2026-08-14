package com.schoolbus.booking.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingResult;
import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingTransaction;
import com.schoolbus.booking.application.tripcancellation.TripCancellationRequestedEnvelope;
import com.schoolbus.booking.application.tripcancellation.TripCancellationRequestedMessage;
import com.schoolbus.transport.infrastructure.messaging.TripCancellationRetryAttemptResolver;
import com.schoolbus.transport.infrastructure.messaging.TripCancellationRetryLane;
import com.schoolbus.transport.infrastructure.messaging.TripCancellationRetryProperties;
import com.schoolbus.transport.infrastructure.messaging.TripCancellationRetryPublisher;
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
public class TripCancellationRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(
            TripCancellationRequestedListener.class
    );

    private final TripCancellationBookingTransaction transaction;
    private final ObjectMapper objectMapper;
    private final TripCancellationRetryPublisher retryPublisher;
    private final TripCancellationRetryAttemptResolver attemptResolver;
    private final TripCancellationRetryProperties retryProperties;

    public TripCancellationRequestedListener(
            TripCancellationBookingTransaction transaction,
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
            queues = "${school-bus.messaging.trip-cancellation.requested-queue}"
    )
    public void consume(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            String eventId = message.getMessageProperties().getMessageId();
            TripCancellationRequestedMessage payload = objectMapper.readValue(
                    message.getBody(),
                    TripCancellationRequestedMessage.class
            );
            TripCancellationBookingResult result = transaction.process(
                    new TripCancellationRequestedEnvelope(eventId, payload)
            );
            channel.basicAck(tag, false);
            log.info(
                    "Trip cancellation bookings handled: tripId={}, cancelled={}, refunds={}, duplicate={}",
                    result.tripId(),
                    result.cancelledBookings(),
                    result.refundsRequested(),
                    result.duplicateEvent()
            );
        } catch (IOException | IllegalArgumentException exception) {
            log.error("Malformed trip cancellation request", exception);
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
                retryProperties.requestedQueue()
        );
        if (completedRetries >= retryProperties.maximumRetries()) {
            log.error(
                    "Trip cancellation request exhausted {} retries",
                    retryProperties.maximumRetries(),
                    processingFailure
            );
            channel.basicReject(tag, false);
            return;
        }
        try {
            retryPublisher.scheduleRetry(
                    message,
                    TripCancellationRetryLane.REQUESTED
            );
            channel.basicAck(tag, false);
            log.warn(
                    "Trip cancellation request scheduled for retry {}/{} after {}",
                    completedRetries + 1,
                    retryProperties.maximumRetries(),
                    retryProperties.delay(),
                    processingFailure
            );
        } catch (RuntimeException publishFailure) {
            processingFailure.addSuppressed(publishFailure);
            log.error(
                    "Failed to publish cancellation request retry; original will be requeued",
                    processingFailure
            );
            channel.basicNack(tag, false, true);
        }
    }
}
