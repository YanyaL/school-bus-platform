package com.schoolbus.booking.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingResult;
import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingTransaction;
import com.schoolbus.transport.infrastructure.messaging.TripCancellationRetryAttemptResolver;
import com.schoolbus.transport.infrastructure.messaging.TripCancellationRetryLane;
import com.schoolbus.transport.infrastructure.messaging.TripCancellationRetryProperties;
import com.schoolbus.transport.infrastructure.messaging.TripCancellationRetryPublisher;
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

class TripCancellationRequestedListenerTest {

    private final TripCancellationBookingTransaction transaction = mock(
            TripCancellationBookingTransaction.class
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
    private final TripCancellationRequestedListener listener =
            new TripCancellationRequestedListener(
                    transaction,
                    new ObjectMapper().findAndRegisterModules(),
                    retryPublisher,
                    attemptResolver,
                    retryProperties
            );

    @Test
    void shouldAckSuccessfullyProcessedRequest() throws IOException {
        when(transaction.process(any())).thenReturn(
                new TripCancellationBookingResult(5001L, 2, 1, false)
        );

        listener.consume(validMessage(), channel);

        verify(channel).basicAck(101L, false);
    }

    @Test
    void shouldRejectMalformedRequestWithoutRequeue() throws IOException {
        listener.consume(message("not-json"), channel);

        verify(channel).basicReject(101L, false);
    }

    @Test
    void shouldScheduleTemporaryFailureAndAckOriginal() throws IOException {
        when(transaction.process(any())).thenThrow(
                new IllegalStateException("database unavailable")
        );
        Message message = validMessage();

        listener.consume(message, channel);

        verify(retryPublisher).scheduleRetry(
                message,
                TripCancellationRetryLane.REQUESTED
        );
        verify(channel).basicAck(101L, false);
    }

    @Test
    void shouldRejectRequestAfterMaximumRetries() throws IOException {
        Message message = validMessage();
        when(transaction.process(any())).thenThrow(
                new IllegalStateException("database unavailable")
        );
        when(attemptResolver.completedRetries(
                message,
                retryProperties.requestedQueue()
        )).thenReturn(3);

        listener.consume(message, channel);

        verify(channel).basicReject(101L, false);
    }

    @Test
    void shouldRequeueOriginalWhenRetryPublishFails() throws IOException {
        Message message = validMessage();
        when(transaction.process(any())).thenThrow(
                new IllegalStateException("database unavailable")
        );
        doThrow(new IllegalStateException("RabbitMQ unavailable"))
                .when(retryPublisher)
                .scheduleRetry(
                        message,
                        TripCancellationRetryLane.REQUESTED
                );

        listener.consume(message, channel);

        verify(channel).basicNack(101L, false, true);
    }

    private Message validMessage() {
        return message("""
                {
                  "tripId": 5001,
                  "tripVersion": 2,
                  "requestedAt": "2026-08-14T10:00:00Z"
                }
                """);
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("11111111-1111-1111-1111-111111111111");
        properties.setDeliveryTag(101L);
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
