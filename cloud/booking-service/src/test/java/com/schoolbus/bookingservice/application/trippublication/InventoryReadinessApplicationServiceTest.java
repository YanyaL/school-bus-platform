package com.schoolbus.bookingservice.application.trippublication;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryReadinessApplicationServiceTest {
    private final InventoryReadinessStore store = mock(
            InventoryReadinessStore.class
    );
    private final InventoryReadinessTransaction transaction = mock(
            InventoryReadinessTransaction.class
    );

    @Test
    void isolatesOneCandidateFailureFromTheRestOfTheBatch() {
        var first = candidate(1L);
        var second = candidate(2L);
        var third = candidate(3L);
        when(store.findCandidates(10)).thenReturn(List.of(first, second, third));
        when(transaction.verify(first)).thenReturn(observation(
                first,
                InventoryReadinessObservation.Status.READY,
                null
        ));
        when(transaction.verify(second)).thenThrow(
                new IllegalStateException("temporary database failure")
        );
        when(transaction.verify(third)).thenReturn(observation(
                third,
                InventoryReadinessObservation.Status.WAITING,
                "INVENTORY_MISSING"
        ));

        var result = new InventoryReadinessApplicationService(
                store,
                transaction,
                10
        ).verifyPending();

        assertThat(result).isEqualTo(
                new InventoryReadinessResult(3, 1, 1, 1)
        );
    }

    private InventoryReadinessCandidate candidate(long tripId) {
        return new InventoryReadinessCandidate(
                tripId,
                "11111111-1111-4111-8111-11111111111" + tripId,
                1L,
                "{\"seatNumbers\":[\"A01\"]}"
        );
    }

    private InventoryReadinessObservation observation(
            InventoryReadinessCandidate candidate,
            InventoryReadinessObservation.Status status,
            String diagnostic
    ) {
        return new InventoryReadinessObservation(
                candidate.tripId(),
                candidate.tripNumber(),
                candidate.publicationVersion(),
                1,
                status == InventoryReadinessObservation.Status.READY ? 1 : null,
                status == InventoryReadinessObservation.Status.READY ? 1 : 0,
                status,
                diagnostic,
                Instant.parse("2026-09-02T06:00:00Z")
        );
    }
}
