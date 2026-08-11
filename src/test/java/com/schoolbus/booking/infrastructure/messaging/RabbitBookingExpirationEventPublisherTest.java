package com.schoolbus.booking.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.booking.application.booking.BookingPaymentDeadlineEvent;
import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitBookingExpirationEventPublisherTest {

    private static final Instant NOW =
            Instant.parse("2026-08-11T00:00:00Z");
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    @Test
    void shouldPublishPersistentMessageWithRemainingDelay() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(
                    new CorrelationData.Confirm(true, null)
            );
            return null;
        }).when(rabbitTemplate).send(
                eq("schoolbus.booking.expiration.delay"),
                eq("booking.payment.deadline.delay"),
                any(Message.class),
                any(CorrelationData.class)
        );
        RabbitBookingExpirationEventPublisher publisher =
                new RabbitBookingExpirationEventPublisher(
                        rabbitTemplate,
                        properties(),
                        relayProperties(),
                        new ObjectMapper().findAndRegisterModules(),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        publisher.publish(event());

        ArgumentCaptor<Message> captor =
                ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                eq("schoolbus.booking.expiration.delay"),
                eq("booking.payment.deadline.delay"),
                captor.capture(),
                any(CorrelationData.class)
        );
        assertThat(captor.getValue().getMessageProperties().getExpiration())
                .isEqualTo("900000");
        assertThat(captor.getValue().getMessageProperties().getMessageId())
                .isEqualTo("event-5001");
        assertThat(captor.getValue().getMessageProperties().getType())
                .isEqualTo(BookingPaymentDeadlineEvent.TYPE);
    }

    private ClaimedOutboxEvent event() {
        return new ClaimedOutboxEvent(
                1L,
                "event-5001",
                BookingPaymentDeadlineEvent.TYPE,
                "{\"bookingId\":5001,"
                        + "\"bookingNumber\":"
                        + "\"55555555-5555-5555-5555-555555555555\","
                        + "\"expiresAt\":\"2026-08-11T00:15:00Z\","
                        + "\"occurredAt\":\"2026-08-11T00:00:00Z\"}",
                "trace-1",
                0,
                NOW,
                1L
        );
    }

    private BookingExpirationMessagingProperties properties() {
        return new BookingExpirationMessagingProperties(
                "schoolbus.booking.expiration.delay",
                "booking.payment.deadline.delay",
                "schoolbus.booking.expiration.delay",
                "schoolbus.booking.events",
                "booking.payment.deadline.reached",
                "schoolbus.booking.expiration",
                "schoolbus.booking.dlx",
                "booking.expiration.dead",
                "schoolbus.booking.expiration.dlq"
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
