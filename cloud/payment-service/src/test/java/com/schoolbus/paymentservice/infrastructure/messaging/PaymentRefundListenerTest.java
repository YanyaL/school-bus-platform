package com.schoolbus.paymentservice.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.paymentservice.application.refund.PaymentRefundApplicationService;
import com.schoolbus.paymentservice.application.refund.RefundProcessingOutcome;
import com.schoolbus.paymentservice.application.refund.RefundProcessingResult;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class PaymentRefundListenerTest {

    private final PaymentRefundApplicationService service = mock(
            PaymentRefundApplicationService.class
    );
    private final Channel channel = mock(Channel.class);
    private final RefundRetryPublisher retryPublisher = mock(
            RefundRetryPublisher.class
    );
    private final RefundRetryAttemptResolver attemptResolver = mock(
            RefundRetryAttemptResolver.class
    );
    private final RefundRetryProperties retryProperties =
            new RefundRetryProperties(
                    "schoolbus.payment.retry",
                    "payment.refund.retry",
                    "schoolbus.payment.refund.retry",
                    Duration.ofSeconds(30),
                    3,
                    Duration.ofSeconds(5)
            );
    private final PaymentRefundListener listener = new PaymentRefundListener(
            service,
            new ObjectMapper().findAndRegisterModules(),
            retryPublisher,
            attemptResolver,
            retryProperties
    );

    @Test
    void shouldAckSuccessfullyProcessedMessage() throws IOException {
        when(service.process(any())).thenReturn(
                new RefundProcessingResult(
                        RefundProcessingOutcome.REFUNDED,
                        "77777777-7777-7777-7777-777777777777",
                        "refund-001"
                )
        );

        listener.consume(validMessage(), channel);

        verify(channel).basicAck(101L, false);
    }

    @Test
    void shouldRejectMalformedMessageWithoutRequeue()
            throws IOException {
        listener.consume(message("not-json"), channel);

        verify(channel).basicReject(101L, false);
    }

    @Test
    void shouldScheduleTemporaryFailureAndAckOriginalMessage()
            throws IOException {
        when(service.process(any())).thenThrow(
                new IllegalStateException("provider unavailable")
        );

        listener.consume(validMessage(), channel);

        verify(retryPublisher).scheduleRetry(any(Message.class));
        verify(channel).basicAck(101L, false);
    }

    @Test
    void shouldRejectMessageAfterMaximumRetries()
            throws IOException {
        Message message = validMessage();
        when(service.process(any())).thenThrow(
                new IllegalStateException("provider unavailable")
        );
        when(attemptResolver.completedRetries(
                message,
                retryProperties.queue()
        )).thenReturn(3);

        listener.consume(message, channel);

        verify(channel).basicReject(101L, false);
    }

    @Test
    void shouldRequeueOriginalWhenRetryPublishFails()
            throws IOException {
        Message message = validMessage();
        when(service.process(any())).thenThrow(
                new IllegalStateException("provider unavailable")
        );
        doThrow(new OutboxPublishException("broker unavailable"))
                .when(retryPublisher).scheduleRetry(message);

        listener.consume(message, channel);

        verify(channel).basicNack(101L, false, true);
    }

    @Test
    void shouldAcceptRefundRequestedEventType() throws IOException {
        when(service.process(any())).thenReturn(
                new RefundProcessingResult(
                        RefundProcessingOutcome.REFUNDED,
                        "77777777-7777-7777-7777-777777777777",
                        "refund-001"
                )
        );
        Message message = validMessage();
        message.getMessageProperties().setHeader(
                "eventType",
                "RefundRequested"
        );

        listener.consume(message, channel);

        verify(channel).basicAck(101L, false);
    }

    @Test
    void shouldRetryWhenPaymentIsTemporarilyMissing()
            throws IOException {
        when(service.process(any())).thenThrow(
                new com.schoolbus.paymentservice.application.refund
                        .RefundPaymentNotFoundException(
                        "77777777-7777-7777-7777-777777777777"
                )
        );

        listener.consume(validMessage(), channel);

        verify(retryPublisher).scheduleRetry(any(Message.class));
        verify(channel).basicAck(101L, false);
    }

    private Message validMessage() {
        return message("""
                {
                  "paymentNumber": "77777777-7777-7777-7777-777777777777",
                  "bookingNumber": "55555555-5555-5555-5555-555555555555",
                  "amount": 5.50,
                  "reason": "PAYMENT_WINDOW_EXPIRED",
                  "paidAt": "2026-08-10T09:50:00Z",
                  "occurredAt": "2026-08-10T09:55:00Z"
                }
                """);
    }

    private Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("event-1");
        properties.setDeliveryTag(101L);
        return new Message(
                body.getBytes(StandardCharsets.UTF_8),
                properties
        );
    }
}
