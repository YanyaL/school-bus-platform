package com.schoolbus.bookingservice.application.trippublication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReadinessTransactionTest {
    private static final Instant NOW = Instant.parse("2026-09-02T06:00:00Z");
    private final InventoryReadinessStore store = mock(
            InventoryReadinessStore.class
    );
    private final InventoryReadinessTransaction transaction =
            new InventoryReadinessTransaction(
                    store,
                    new ObjectMapper(),
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );
    private final InventoryReadinessCandidate candidate =
            new InventoryReadinessCandidate(
                    101L,
                    "11111111-1111-4111-8111-111111111111",
                    3L,
                    "{\"seatNumbers\":[\"A01\",\"A02\"]}"
            );

    @BeforeEach
    void seatsExist() {
        when(store.findSeatNumbers(101L)).thenReturn(List.of("A01", "A02"));
    }

    @Test
    void marksReadyOnlyWhenInventoryAndExactSeatSetMatch() {
        when(store.findInventoryTotal(101L)).thenReturn(2);

        var result = transaction.verify(candidate);

        assertThat(result.status())
                .isEqualTo(InventoryReadinessObservation.Status.READY);
        assertThat(result.diagnosticCode()).isNull();
        assertThat(result.expectedTotalSeats()).isEqualTo(2);
        assertThat(result.checkedAt()).isEqualTo(NOW);
        verify(store).saveObservation(result);
    }

    @Test
    void remainsWaitingWhenInventoryHasNotBeenInitialized() {
        when(store.findInventoryTotal(101L)).thenReturn(null);

        var result = transaction.verify(candidate);

        assertThat(result.status())
                .isEqualTo(InventoryReadinessObservation.Status.WAITING);
        assertThat(result.diagnosticCode()).isEqualTo("INVENTORY_MISSING");
    }

    @Test
    void detectsInventoryTotalAndSeatSetMismatches() {
        when(store.findInventoryTotal(101L)).thenReturn(3);
        assertThat(transaction.verify(candidate).diagnosticCode())
                .isEqualTo("INVENTORY_TOTAL_MISMATCH");

        when(store.findInventoryTotal(101L)).thenReturn(2);
        when(store.findSeatNumbers(101L)).thenReturn(List.of("A01", "B01"));
        assertThat(transaction.verify(candidate).diagnosticCode())
                .isEqualTo("SEAT_SET_MISMATCH");
    }

    @Test
    void rejectsCorruptedProjectionInsteadOfMarkingItReady() {
        var corrupted = new InventoryReadinessCandidate(
                101L,
                candidate.tripNumber(),
                3L,
                "{\"seatNumbers\":[\"A01\",\"A01\"]}"
        );

        assertThatThrownBy(() -> transaction.verify(corrupted))
                .isInstanceOf(IllegalArgumentException.class);
        verify(store, org.mockito.Mockito.never()).saveObservation(any());
    }
}
