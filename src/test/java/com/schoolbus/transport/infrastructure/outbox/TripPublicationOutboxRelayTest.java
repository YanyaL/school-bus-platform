package com.schoolbus.transport.infrastructure.outbox;

import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;
import com.schoolbus.payment.infrastructure.outbox.MyBatisOutboxRelayRepository;
import com.schoolbus.payment.infrastructure.outbox.OutboxRelayResult;
import com.schoolbus.transport.infrastructure.messaging.TripPublicationEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TripPublicationOutboxRelayTest {
    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");
    private final MyBatisOutboxRelayRepository repository = mock(MyBatisOutboxRelayRepository.class);
    private final TripPublicationEventPublisher publisher = mock(TripPublicationEventPublisher.class);
    private final TripPublicationOutboxRelay relay = new TripPublicationOutboxRelay(repository, publisher,
            new OutboxRelayProperties(true, 50, Duration.ofSeconds(30), Duration.ofSeconds(5),
                    Duration.ofSeconds(5), Duration.ofMinutes(5), 10), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void onlyClaimsTransportTripPublishedAndMarksAfterConfirmation() {
        ClaimedOutboxEvent event = prepare(0);
        assertThat(relay.relayReadyEvents()).isEqualTo(new OutboxRelayResult(1, 1, 0));
        var order = inOrder(publisher, repository);
        order.verify(publisher).publish(event);
        order.verify(repository).markPublished(event, NOW);
    }

    @Test
    void failedPublishSchedulesExponentialBackoffWithoutMarkingPublished() {
        ClaimedOutboxEvent event = prepare(2);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(event);
        assertThat(relay.relayReadyEvents()).isEqualTo(new OutboxRelayResult(1, 0, 1));
        verify(repository).markFailed(event, NOW.plusSeconds(20));
        verify(repository, never()).markPublished(any(), any());
    }

    @Test
    void retriesAmbiguousBrokerAckDatabaseFailureWithSameIdentity() {
        ClaimedOutboxEvent event = prepare(0);
        doThrow(new IllegalStateException("database unavailable")).when(repository).markPublished(event, NOW);
        assertThat(relay.relayReadyEvents()).isEqualTo(new OutboxRelayResult(1, 0, 1));
        verify(publisher).publish(event);
        verify(repository).markFailed(event, NOW.plusSeconds(5));
    }

    @Test
    void stopsAfterConfiguredMaximum() {
        ClaimedOutboxEvent event = prepare(9);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(event);
        relay.relayReadyEvents();
        verify(repository).markFailed(event, null);
    }

    @Test
    void backoffHasUpperBound() {
        ClaimedOutboxEvent event = prepare(8);
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(event);
        relay.relayReadyEvents();
        verify(repository).markFailed(event, NOW.plusSeconds(300));
    }

    @Test
    void failedMarkDoesNotAbortRestOfBatch() {
        ClaimedOutboxEvent first = prepare(0);
        ClaimedOutboxEvent second = new ClaimedOutboxEvent(2, "event-2", "TripPublished", "{}", null, 0, NOW, 1);
        when(repository.claimReady("transport", "TripPublished", NOW, 50, Duration.ofSeconds(30)))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(first);
        doThrow(new IllegalStateException("database unavailable")).when(repository).markFailed(first, NOW.plusSeconds(5));
        assertThat(relay.relayReadyEvents()).isEqualTo(new OutboxRelayResult(2, 1, 1));
        verify(repository).markPublished(second, NOW);
    }

    private ClaimedOutboxEvent prepare(int retries) {
        ClaimedOutboxEvent event = new ClaimedOutboxEvent(1, "event-1", "TripPublished", "{}", null, retries, NOW, 1);
        when(repository.claimReady("transport", "TripPublished", NOW, 50, Duration.ofSeconds(30)))
                .thenReturn(List.of(event));
        return event;
    }
}
