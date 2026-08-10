package com.schoolbus.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.schoolbus.payment.application.refund.PaymentRefundApplicationService;
import com.schoolbus.payment.application.refund.RefundProcessingOutcome;
import com.schoolbus.payment.application.refund.RefundProcessingResult;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRefundListenerTest {

    private final PaymentRefundApplicationService service = mock(
            PaymentRefundApplicationService.class
    );
    private final Channel channel = mock(Channel.class);
    private final PaymentRefundListener listener = new PaymentRefundListener(
            service,
            new ObjectMapper().findAndRegisterModules()
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
    void shouldNackTemporaryFailureWithoutImmediateRequeue()
            throws IOException {
        when(service.process(any())).thenThrow(
                new IllegalStateException("provider unavailable")
        );

        listener.consume(validMessage(), channel);

        verify(channel).basicNack(101L, false, false);
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
