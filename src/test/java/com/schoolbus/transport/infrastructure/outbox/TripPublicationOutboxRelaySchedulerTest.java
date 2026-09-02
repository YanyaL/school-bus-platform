package com.schoolbus.transport.infrastructure.outbox;

import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.payment.infrastructure.outbox.OutboxRelayResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class TripPublicationOutboxRelaySchedulerTest {
    private final TripPublicationOutboxRelay relay = mock(TripPublicationOutboxRelay.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TripPublicationOutboxRelayScheduler.class)
            .withBean(TripPublicationOutboxRelay.class, () -> relay)
            .withBean(OutboxRelayProperties.class, () -> properties(true));

    @Test
    void schedulerRequiresBothExplicitFlags() {
        runner.run(context -> assertThat(context).doesNotHaveBean(TripPublicationOutboxRelayScheduler.class));
        runner.withPropertyValues("school-bus.transport.publication-events.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(TripPublicationOutboxRelayScheduler.class));
        runner.withPropertyValues("school-bus.transport.publication-events.relay-enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(TripPublicationOutboxRelayScheduler.class));
        runner.withPropertyValues("school-bus.transport.publication-events.enabled=true",
                        "school-bus.transport.publication-events.relay-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(TripPublicationOutboxRelayScheduler.class));
    }

    @Test
    void globalRelayStopSwitchIsRespected() {
        new TripPublicationOutboxRelayScheduler(relay, properties(false)).relay();
        verifyNoInteractions(relay);
    }

    @Test
    void enabledSchedulerDelegates() {
        when(relay.relayReadyEvents()).thenReturn(new OutboxRelayResult(1, 1, 0));
        new TripPublicationOutboxRelayScheduler(relay, properties(true)).relay();
        verify(relay).relayReadyEvents();
    }

    @Test
    void scanFailureDoesNotTerminateScheduler() {
        when(relay.relayReadyEvents()).thenThrow(new IllegalStateException("database unavailable"));
        assertThatCode(() -> new TripPublicationOutboxRelayScheduler(relay, properties(true)).relay())
                .doesNotThrowAnyException();
    }

    private static OutboxRelayProperties properties(boolean enabled) {
        return new OutboxRelayProperties(enabled, 50, Duration.ofSeconds(30), Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofMinutes(5), 10);
    }
}
