package com.schoolbus.payment.infrastructure.outbox;

import com.schoolbus.payment.infrastructure.messaging.OutboxEventPublisher;
import com.schoolbus.payment.infrastructure.messaging.OutboxPublishException;
import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class PaymentRefundOutboxRelayTest {

    private static final Instant NOW = Instant.parse(
            "2026-08-10T10:00:00Z"
    );

    private final MyBatisOutboxRelayRepository repository = mock(
            MyBatisOutboxRelayRepository.class
    );
    private final OutboxEventPublisher publisher = mock(
            OutboxEventPublisher.class
    );

    @Test
    void shouldMarkBrokerConfirmedEventAsPublished() {
        ClaimedOutboxEvent event = event(0);
        when(repository.claimReady(
                NOW,
                50,
                Duration.ofSeconds(30)
        )).thenReturn(List.of(event));
        PaymentRefundOutboxRelay relay = relay(10);

        OutboxRelayResult result = relay.relayReadyEvents();

        assertThat(result).isEqualTo(new OutboxRelayResult(1, 1, 0));
        verify(publisher).publish(event);
        verify(repository).markPublished(event, NOW);
    }

    @Test
    void shouldScheduleExponentialRetryWhenPublishingFails() {
        ClaimedOutboxEvent event = event(2);
        when(repository.claimReady(
                NOW,
                50,
                Duration.ofSeconds(30)
        )).thenReturn(List.of(event));
        doThrow(new OutboxPublishException("broker unavailable"))
                .when(publisher).publish(event);

        OutboxRelayResult result = relay(10).relayReadyEvents();

        assertThat(result).isEqualTo(new OutboxRelayResult(1, 0, 1));
        verify(repository).markFailed(
                event,
                NOW.plusSeconds(20)
        );
    }

    @Test
    void shouldStopRetryingAfterMaximumAttempts() {
        ClaimedOutboxEvent event = event(9);
        when(repository.claimReady(
                NOW,
                50,
                Duration.ofSeconds(30)
        )).thenReturn(List.of(event));
        doThrow(new OutboxPublishException("broker unavailable"))
                .when(publisher).publish(event);

        OutboxRelayResult result = relay(10).relayReadyEvents();

        assertThat(result).isEqualTo(new OutboxRelayResult(1, 0, 1));
        verify(repository).markFailed(event, null);
        verify(repository, never()).markPublished(event, NOW);
    }

    private PaymentRefundOutboxRelay relay(int maximumAttempts) {
        return new PaymentRefundOutboxRelay(
                repository,
                publisher,
                new OutboxRelayProperties(
                        true,
                        50,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(5),
                        Duration.ofMinutes(5),
                        maximumAttempts
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private ClaimedOutboxEvent event(int retryCount) {
        return new ClaimedOutboxEvent(
                1L,
                "event-1",
                "PaymentRefundRequired",
                "{\"paymentNumber\":\"PAY-2026-0001\"}",
                "trace-1",
                retryCount,
                NOW.minusSeconds(10),
                1L
        );
    }
}
