package com.schoolbus.booking.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.booking.application.payment.PaymentSucceededBookingTransaction;
import com.schoolbus.booking.application.payment.PaymentSucceededOutcome;
import com.schoolbus.booking.application.payment.PaymentSucceededResult;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSucceededListenerTest {

    private final PaymentSucceededBookingTransaction transaction = mock(
            PaymentSucceededBookingTransaction.class
    );
    private final Channel channel = mock(Channel.class);
    private final PaymentSucceededRetryPublisher retryPublisher = mock(
            PaymentSucceededRetryPublisher.class
    );
    private final PaymentSucceededRetryAttemptResolver attemptResolver = mock(
            PaymentSucceededRetryAttemptResolver.class
    );
    private final PaymentSucceededRetryProperties retryProperties =
            new PaymentSucceededRetryProperties(
                    "schoolbus.booking.retry",
                    "payment.succeeded.retry",
                    "schoolbus.booking.payment-succeeded.retry",
                    Duration.ofSeconds(30),
                    3,
                    Duration.ofSeconds(5)
            );
    private final PaymentSucceededListener listener =
            new PaymentSucceededListener(
                    transaction,
                    new ObjectMapper().findAndRegisterModules(),
                    retryPublisher,
                    attemptResolver,
                    retryProperties
            );

    @Test
    void shouldAckValidPaymentSucceededMessage() throws Exception {
        when(transaction.process(any())).thenReturn(
                new PaymentSucceededResult(
                        "99999999-9999-9999-9999-999999999999",
                        "88888888-8888-8888-8888-888888888888",
                        PaymentSucceededOutcome.ALREADY_APPLIED
                )
        );

        listener.consume(message(validPayload()), channel);

        verify(transaction).process(any());
        verify(channel).basicAck(7L, false);
    }

    @Test
    void shouldRejectMalformedMessageWithoutRequeue() throws Exception {
        listener.consume(message("{not-json}"), channel);

        verify(channel).basicReject(7L, false);
        verify(retryPublisher, never()).scheduleRetry(any());
    }

    @Test
    void shouldScheduleFiniteRetryForTechnicalFailure() throws Exception {
        when(transaction.process(any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(attemptResolver.completedRetries(
                any(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(0);
        Message message = message(validPayload());

        listener.consume(message, channel);

        verify(retryPublisher).scheduleRetry(message);
        verify(channel).basicAck(7L, false);
    }

    @Test
    void shouldDeadLetterAfterRetryBudgetIsExhausted() throws Exception {
        when(transaction.process(any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(attemptResolver.completedRetries(
                any(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(3);

        listener.consume(message(validPayload()), channel);

        verify(channel).basicReject(7L, false);
        verify(retryPublisher, never()).scheduleRetry(any());
    }

    @Test
    void shouldRequeueOriginalWhenRetryPublishFails() throws Exception {
        when(transaction.process(any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        when(attemptResolver.completedRetries(
                any(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(0);
        doThrow(new PaymentSucceededRetryPublishException("broker down"))
                .when(retryPublisher)
                .scheduleRetry(any());

        listener.consume(message(validPayload()), channel);

        verify(channel).basicNack(7L, false, true);
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("event-1");
        properties.setDeliveryTag(7L);
        return new Message(
                body.getBytes(StandardCharsets.UTF_8),
                properties
        );
    }

    private String validPayload() {
        return """
                {
                  "schemaVersion": 1,
                  "paymentNumber": "99999999-9999-9999-9999-999999999999",
                  "bookingNumber": "88888888-8888-8888-8888-888888888888",
                  "amount": 12.50,
                  "paidAt": "2026-08-21T09:59:50Z",
                  "occurredAt": "2026-08-21T10:00:00Z"
                }
                """;
    }
}
