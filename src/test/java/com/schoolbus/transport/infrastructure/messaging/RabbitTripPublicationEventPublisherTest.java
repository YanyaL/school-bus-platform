package com.schoolbus.transport.infrastructure.messaging;

import com.schoolbus.payment.infrastructure.messaging.OutboxPublishException;
import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RabbitTripPublicationEventPublisherTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final RabbitTripPublicationEventPublisher publisher = new RabbitTripPublicationEventPublisher(rabbit,
            new TripPublicationMessagingProperties("publication-test", "trip.published.v1", "shadow-test", 10),
            new OutboxRelayProperties(true, 50, Duration.ofSeconds(30), Duration.ofMillis(10),
                    Duration.ofSeconds(5), Duration.ofMinutes(5), 3));

    @Test
    void confirmsPersistentMessageWithStableEventIdentityAndOriginalPayload() {
        confirm(true, false);
        publisher.publish(event("TripPublished"));
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("publication-test"), eq("trip.published.v1"), captor.capture(), any(CorrelationData.class));
        assertThat(captor.getValue().getBody()).isEqualTo("{\"tripId\":\"9007199254740993\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var properties = captor.getValue().getMessageProperties();
        assertThat(properties.getMessageId()).isEqualTo("event-1");
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getHeaders()).containsEntry("schemaVersion", 1).containsEntry("traceId", "trace-1");
    }

    @Test
    void rejectsNack() {
        confirm(false, false);
        assertThatThrownBy(() -> publisher.publish(event("TripPublished")))
                .isInstanceOf(OutboxPublishException.class).hasMessageContaining("rejected");
    }

    @Test
    void returnedMessageIsFailureEvenWhenBrokerAcknowledges() {
        confirm(true, true);
        assertThatThrownBy(() -> publisher.publish(event("TripPublished")))
                .isInstanceOf(OutboxPublishException.class).hasMessageContaining("returned");
    }

    @Test
    void boundsWaitForMissingConfirmation() {
        assertThatThrownBy(() -> publisher.publish(event("TripPublished")))
                .isInstanceOf(OutboxPublishException.class).hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    @Test
    void preservesInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> publisher.publish(event("TripPublished")))
                    .isInstanceOf(OutboxPublishException.class).hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void neverSendsOtherEventTypes() {
        assertThatThrownBy(() -> publisher.publish(event("PaymentSucceeded")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rabbit);
    }

    private void confirm(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312, "NO_ROUTE",
                        "publication-test", "trip.published.v1"));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "rejected"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private ClaimedOutboxEvent event(String type) {
        return new ClaimedOutboxEvent(1, "event-1", type, "{\"tripId\":\"9007199254740993\"}",
                "trace-1", 0, Instant.parse("2026-08-31T00:00:00Z"), 1);
    }
}
