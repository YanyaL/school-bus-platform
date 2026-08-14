package com.schoolbus.transport.infrastructure.booking;

import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LocalTripInventoryInitializerTest {

    @Mock
    private SeatInventoryRepository repository;

    @Test
    void shouldInitializeInventoryWithAllSeatsAvailable() {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        LocalTripInventoryInitializer initializer =
                new LocalTripInventoryInitializer(repository);

        initializer.initialize(5001L, 40, now);

        ArgumentCaptor<SeatInventory> captor =
                ArgumentCaptor.forClass(SeatInventory.class);
        verify(repository).save(captor.capture());
        SeatInventory inventory = captor.getValue();
        assertThat(inventory.tripReference().value()).isEqualTo(5001L);
        assertThat(inventory.totalSeats()).isEqualTo(40);
        assertThat(inventory.availableSeats()).isEqualTo(40);
        assertThat(inventory.version()).isZero();
    }
}
