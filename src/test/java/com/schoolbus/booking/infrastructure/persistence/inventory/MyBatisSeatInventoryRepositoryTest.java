package com.schoolbus.booking.infrastructure.persistence.inventory;

import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.trip.TripReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisSeatInventoryRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-08T00:00:00Z");

    @Mock
    private SeatInventoryMapper mapper;

    private MyBatisSeatInventoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisSeatInventoryRepository(mapper);
    }

    @Test
    void shouldInsertNewInventory() {
        SeatInventory inventory = inventory();
        when(mapper.insertInventory(any())).thenReturn(1);

        repository.save(inventory);

        ArgumentCaptor<SeatInventoryDataObject> captor =
                ArgumentCaptor.forClass(
                        SeatInventoryDataObject.class
                );
        verify(mapper).insertInventory(captor.capture());
        assertThat(captor.getValue().getTripId())
                .isEqualTo(2001L);
        assertThat(captor.getValue().getTotalSeats())
                .isEqualTo(20);
        assertThat(captor.getValue().getAvailableSeats())
                .isEqualTo(20);
    }

    @Test
    void shouldUpdateInventoryUsingPreviousVersion() {
        SeatInventory inventory = inventory();
        inventory.reserve(CREATED_AT.plusSeconds(60));
        when(mapper.updateWithVersion(any(), any()))
                .thenReturn(1);

        repository.save(inventory);

        ArgumentCaptor<SeatInventoryDataObject> inventoryCaptor =
                ArgumentCaptor.forClass(
                        SeatInventoryDataObject.class
                );
        ArgumentCaptor<Long> versionCaptor =
                ArgumentCaptor.forClass(Long.class);
        verify(mapper).updateWithVersion(
                inventoryCaptor.capture(),
                versionCaptor.capture()
        );
        assertThat(inventoryCaptor.getValue().getAvailableSeats())
                .isEqualTo(19);
        assertThat(inventoryCaptor.getValue().getVersion())
                .isEqualTo(1L);
        assertThat(versionCaptor.getValue()).isZero();
    }

    @Test
    void shouldReportOptimisticLockConflict() {
        SeatInventory inventory = inventory();
        inventory.reserve(CREATED_AT.plusSeconds(60));
        when(mapper.updateWithVersion(any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> repository.save(inventory))
                .isInstanceOf(
                        OptimisticLockingFailureException.class
                )
                .hasMessage(
                        "seat inventory was modified by another request"
                );
    }

    @Test
    void shouldRestoreInventoryFromDataObject() {
        SeatInventoryDataObject dataObject = dataObject();
        when(mapper.selectByTripId(2001L)).thenReturn(dataObject);

        Optional<SeatInventory> result = repository
                .findByTripReference(TripReference.of(2001L));

        assertThat(result).isPresent();
        SeatInventory restored = result.orElseThrow();
        assertThat(restored.totalSeats()).isEqualTo(20);
        assertThat(restored.availableSeats()).isEqualTo(7);
        assertThat(restored.version()).isEqualTo(13L);
    }

    private SeatInventory inventory() {
        return SeatInventory.initialize(
                TripReference.of(2001L),
                20,
                CREATED_AT
        );
    }

    private SeatInventoryDataObject dataObject() {
        SeatInventoryDataObject dataObject =
                new SeatInventoryDataObject();
        dataObject.setTripId(2001L);
        dataObject.setTotalSeats(20);
        dataObject.setAvailableSeats(7);
        dataObject.setVersion(13L);
        dataObject.setCreatedAt(
                LocalDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC)
        );
        dataObject.setUpdatedAt(
                LocalDateTime.ofInstant(
                        CREATED_AT.plusSeconds(300),
                        ZoneOffset.UTC
                )
        );
        return dataObject;
    }
}
