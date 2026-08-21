package com.schoolbus.booking.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitPaymentSucceededRetryPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitPaymentSucceededRetryPublisher publisher =
            new RabbitPaymentSucceededRetryPublisher(
                    rabbitTemplate,
                    properties()
            );

    @Test
    void shouldPublishRetryAndWaitForBrokerAck() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );
            return null;
        }).when(rabbitTemplate).send(
                eq("schoolbus.booking.retry"),
                eq("payment.succeeded.retry"),
                any(Message.class),
                any(CorrelationData.class)
        );
        Message original = message();

        publisher.scheduleRetry(original);

        ArgumentCaptor<Message> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq("schoolbus.booking.retry"),
                eq("payment.succeeded.retry"),
                captor.capture(),
                any(CorrelationData.class)
        );
        assertThat(captor.getValue().getBody())
                .containsExactly(original.getBody());
        assertThat(captor.getValue().getMessageProperties().getMessageId())
                .isEqualTo("event-1");
    }

    @Test
    void shouldFailWhenBrokerRejectsRetryMessage() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(false, "queue unavailable")
            );
            return null;
        }).when(rabbitTemplate).send(
                any(String.class),
                any(String.class),
                any(Message.class),
                any(CorrelationData.class)
        );

        assertThatThrownBy(() -> publisher.scheduleRetry(message()))
                .isInstanceOf(
                        PaymentSucceededRetryPublishException.class
                )
                .hasMessageContaining(
                        "rejected payment succeeded retry"
                );
    }

    private Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("event-1");
        return new Message(
                "{\"paymentNumber\":\"PAY-1\"}"
                        .getBytes(StandardCharsets.UTF_8),
                properties
        );
    }

    private PaymentSucceededRetryProperties properties() {
        return new PaymentSucceededRetryProperties(
                "schoolbus.booking.retry",
                "payment.succeeded.retry",
                "schoolbus.booking.payment-succeeded.retry",
                Duration.ofSeconds(30),
                3,
                Duration.ofSeconds(5)
        );
    }
}
