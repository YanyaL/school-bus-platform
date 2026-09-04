package com.schoolbus.bookingservice.infrastructure.persistence.trippublication;

import com.schoolbus.bookingservice.domain.trip.TripReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisInventoryReadinessGateTest {

    @Test
    void requiresReadyObservationForTheLatestPublishedVersion() {
        InventoryReadinessMapper mapper = mock(
                InventoryReadinessMapper.class
        );
        when(mapper.isReadyForPublication(42L, 7L)).thenReturn(true);
        MyBatisInventoryReadinessGate gate =
                new MyBatisInventoryReadinessGate(mapper);

        assertThat(gate.isReady(TripReference.of(42L), 7L)).isTrue();
        assertThat(gate.isReady(TripReference.of(42L), 8L)).isFalse();
    }
}
