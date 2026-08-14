package com.schoolbus.transport.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.transport.application.trip.TripCancellationCompletionTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripCancellationSettledListenerTest {

    private final TripCancellationCompletionTransaction transaction = mock(
            TripCancellationCompletionTransaction.class
    );
    private final Channel channel = mock(Channel.class);
    private final TripCancellationRetryPublisher retryPublisher = mock(
            TripCancellationRetryPublisher.class
    );
    private final TripCancellationRetryAttemptResolver attemptResolver = mock(
            TripCancellationRetryAttemptResolver.class
    );
    private final TripCancellationRetryProperties retryProperties =
            retryProperties();
    private final TripCancellationSettledListener listener =
            new TripCancellationSettledListener(
                    transaction,
                    new ObjectMapper().findAndRegisterModules(),
                    retryPublisher,
                    attemptResolver,
                    retryProperties
            );

    @Test
    void shouldAckSuccessfullyProcessedEvent() throws IOException {
        when(transaction.complete(any())).thenReturn(true);

        listener.consume(validMessage(), channel);

        verify(channel).basicAck(202L, false);
    }

    @Test
    void shouldRejectMalformedEventWithoutRequeue() throws IOException {
        listener.consume(message("not-json"), channel);

        verify(channel).basicReject(202L, false);
    }

    @Test
    void shouldScheduleTemporaryFailureAndAckOriginal() throws IOException {
        Message message = validMessage();
        when(transaction.complete(any())).thenThrow(
                new IllegalStateException("database unavailable")
        );

        listener.consume(message, channel);

        verify(retryPublisher).scheduleRetry(
                message,
                TripCancellationRetryLane.SETTLED
        );
        verify(channel).basicAck(202L, false);
    }

    @Test
    void shouldRejectEventAfterMaximumRetries() throws IOException {
        Message message = validMessage();
        when(transaction.complete(any())).thenThrow(
                new IllegalStateException("database unavailable")
        );
        when(attemptResolver.completedRetries(
                message,
                retryProperties.settledQueue()
        )).thenReturn(3);

        listener.consume(message, channel);

        verify(channel).basicReject(202L, false);
    }

    @Test
    void shouldRequeueOriginalWhenRetryPublishFails() throws IOException {
        Message message = validMessage();
        when(transaction.complete(any())).thenThrow(
                new IllegalStateException("database unavailable")
        );
        doThrow(new IllegalStateException("RabbitMQ unavailable"))
                .when(retryPublisher)
                .scheduleRetry(message, TripCancellationRetryLane.SETTLED);

        listener.consume(message, channel);

        verify(channel).basicNack(202L, false, true);
    }

    private Message validMessage() {
        return message("""
                {
                  "tripId": 5001,
                  "settledAt": "2026-08-14T10:00:00Z"
                }
                """);
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("22222222-2222-2222-2222-222222222222");
        properties.setDeliveryTag(202L);
        return new Message(
                body.getBytes(StandardCharsets.UTF_8),
                properties
        );
    }

    private TripCancellationRetryProperties retryProperties() {
        return new TripCancellationRetryProperties(
                "schoolbus.transport.cancellation.retry",
                "trip.cancellation.requested.retry",
                "schoolbus.booking.trip-cancellation.retry",
                "trip.cancellation.settled.retry",
                "schoolbus.transport.trip-cancellation-settled.retry",
                Duration.ofSeconds(30),
                3,
                Duration.ofSeconds(5)
        );
    }
}
