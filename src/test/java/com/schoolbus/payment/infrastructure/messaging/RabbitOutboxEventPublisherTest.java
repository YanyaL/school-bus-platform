package com.schoolbus.payment.infrastructure.messaging;

import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitOutboxEventPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitOutboxEventPublisher publisher =
            new RabbitOutboxEventPublisher(
                    rabbitTemplate,
                    messagingProperties(),
                    relayProperties()
            );

    @Test
    void shouldPublishPersistentJsonAndWaitForBrokerAck() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );
            return null;
        }).when(rabbitTemplate).send(
                eq("schoolbus.payment.events"),
                eq("payment.refund.required"),
                any(Message.class),
                any(CorrelationData.class)
        );

        publisher.publish(event(0));

        ArgumentCaptor<Message> messageCaptor =
                ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq("schoolbus.payment.events"),
                eq("payment.refund.required"),
                messageCaptor.capture(),
                any(CorrelationData.class)
        );
        Message message = messageCaptor.getValue();
        assertThat(new String(message.getBody()))
                .contains("PAY-2026-0001");
        assertThat(message.getMessageProperties().getMessageId())
                .isEqualTo("event-1");
        assertThat(message.getMessageProperties().getType())
                .isEqualTo("PaymentRefundRequired");
        assertThat(message.getMessageProperties().getContentType())
                .isEqualTo("application/json");
        assertThat(message.getMessageProperties().getHeaders())
                .containsEntry("traceId", "trace-1");
    }

    @Test
    void shouldRejectNegativePublisherConfirmation() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(false, "exchange unavailable")
            );
            return null;
        }).when(rabbitTemplate).send(
                any(String.class),
                any(String.class),
                any(Message.class),
                any(CorrelationData.class)
        );

        assertThatThrownBy(() -> publisher.publish(event(0)))
                .isInstanceOf(OutboxPublishException.class)
                .hasMessageContaining("RabbitMQ rejected refund event")
                .hasMessageContaining("exchange unavailable");
    }

    private ClaimedOutboxEvent event(int retryCount) {
        return new ClaimedOutboxEvent(
                1L,
                "event-1",
                "PaymentRefundRequired",
                "{\"paymentNumber\":\"PAY-2026-0001\","
                        + "\"amount\":" + BigDecimal.TEN + "}",
                "trace-1",
                retryCount,
                Instant.parse("2026-08-10T10:00:00Z"),
                1L
        );
    }

    private PaymentMessagingProperties messagingProperties() {
        return new PaymentMessagingProperties(
                "schoolbus.payment.events",
                "payment.refund.required",
                "schoolbus.payment.refund",
                "schoolbus.payment.dlx",
                "payment.refund.dead",
                "schoolbus.payment.refund.dlq"
        );
    }

    private OutboxRelayProperties relayProperties() {
        return new OutboxRelayProperties(
                true,
                50,
                Duration.ofSeconds(30),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                10
        );
    }
}
