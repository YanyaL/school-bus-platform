package com.schoolbus.transport.application.trip;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripCancellationReconciliationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-14T10:00:00Z");

    @Test
    void shouldFinalizeSettledCancellationAfterGracePeriod() {
        TripCancellationReconciliationPort port = mock(
                TripCancellationReconciliationPort.class
        );
        TripCancellationCompletionTransaction transaction = mock(
                TripCancellationCompletionTransaction.class
        );
        when(port.findSettledCancellationsAwaitingFinalization(
                NOW.minus(Duration.ofMinutes(2)),
                50
        )).thenReturn(List.of(5001L, 5002L));
        when(transaction.complete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(true, false);
        TripCancellationReconciliationService service =
                new TripCancellationReconciliationService(
                        port,
                        transaction,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        Duration.ofMinutes(2),
                        50
                );

        TripCancellationReconciliationResult result = service.reconcile();

        assertThat(result).isEqualTo(
                new TripCancellationReconciliationResult(2, 1, 1)
        );
        var captor = forClass(TripCancellationSettledEnvelope.class);
        verify(transaction, org.mockito.Mockito.times(2))
                .complete(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(value -> value.payload().tripId())
                .containsExactly(5001L, 5002L);
        assertThat(captor.getAllValues())
                .extracting(TripCancellationSettledEnvelope::eventId)
                .doesNotHaveDuplicates()
                .allMatch(value -> value.matches(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                ));
    }
}
