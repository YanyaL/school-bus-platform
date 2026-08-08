package com.schoolbus.booking.domain.inventory;

import com.schoolbus.booking.domain.trip.TripReference;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatInventoryTest {

    private static final Instant INITIALIZED_AT =
            Instant.parse("2026-08-08T00:00:00Z");

    @Test
    void shouldInitializeWithAllSeatsAvailable() {
        SeatInventory inventory = inventoryWithTwoSeats();

        assertThat(inventory.tripReference())
                .isEqualTo(TripReference.of(2001L));
        assertThat(inventory.totalSeats()).isEqualTo(2);
        assertThat(inventory.availableSeats()).isEqualTo(2);
        assertThat(inventory.version()).isZero();
        assertThat(inventory.isSoldOut()).isFalse();
    }

    @Test
    void shouldReserveSeatAndIncreaseVersion() {
        SeatInventory inventory = inventoryWithTwoSeats();
        Instant reservedAt = INITIALIZED_AT.plusSeconds(60);

        inventory.reserve(reservedAt);

        assertThat(inventory.availableSeats()).isEqualTo(1);
        assertThat(inventory.version()).isEqualTo(1L);
        assertThat(inventory.updatedAt()).isEqualTo(reservedAt);
    }

    @Test
    void shouldBecomeSoldOutAfterLastSeatIsReserved() {
        SeatInventory inventory = inventoryWithTwoSeats();

        inventory.reserve(INITIALIZED_AT.plusSeconds(60));
        inventory.reserve(INITIALIZED_AT.plusSeconds(120));

        assertThat(inventory.availableSeats()).isZero();
        assertThat(inventory.isSoldOut()).isTrue();
        assertThat(inventory.version()).isEqualTo(2L);
    }

    @Test
    void shouldRejectReservationWhenSoldOutWithoutMutation() {
        SeatInventory inventory = inventoryWithTwoSeats();
        inventory.reserve(INITIALIZED_AT.plusSeconds(60));
        Instant soldOutAt = INITIALIZED_AT.plusSeconds(120);
        inventory.reserve(soldOutAt);

        assertThatThrownBy(
                () -> inventory.reserve(
                        INITIALIZED_AT.plusSeconds(180)
                )
        )
                .isInstanceOf(NoSeatAvailableException.class)
                .hasMessage("no seat available for trip: 2001");

        assertThat(inventory.availableSeats()).isZero();
        assertThat(inventory.version()).isEqualTo(2L);
        assertThat(inventory.updatedAt()).isEqualTo(soldOutAt);
    }

    @Test
    void shouldReleaseReservedSeatAndIncreaseVersion() {
        SeatInventory inventory = inventoryWithTwoSeats();
        inventory.reserve(INITIALIZED_AT.plusSeconds(60));
        Instant releasedAt = INITIALIZED_AT.plusSeconds(120);

        inventory.release(releasedAt);

        assertThat(inventory.availableSeats()).isEqualTo(2);
        assertThat(inventory.version()).isEqualTo(2L);
        assertThat(inventory.updatedAt()).isEqualTo(releasedAt);
    }

    @Test
    void shouldRejectReleaseWhenInventoryIsFullWithoutMutation() {
        SeatInventory inventory = inventoryWithTwoSeats();

        assertThatThrownBy(
                () -> inventory.release(
                        INITIALIZED_AT.plusSeconds(60)
                )
        )
                .isInstanceOf(
                        SeatInventoryOverflowException.class
                )
                .hasMessage(
                        "seat inventory is already full for trip: 2001"
                );

        assertThat(inventory.availableSeats()).isEqualTo(2);
        assertThat(inventory.version()).isZero();
    }

    @Test
    void shouldRejectOutOfOrderReservationWithoutMutation() {
        SeatInventory inventory = inventoryWithTwoSeats();

        assertThatThrownBy(
                () -> inventory.reserve(
                        INITIALIZED_AT.minusSeconds(1)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "changedAt must not be before updatedAt"
                );

        assertThat(inventory.availableSeats()).isEqualTo(2);
        assertThat(inventory.version()).isZero();
    }

    @Test
    void shouldRestorePersistedInventory() {
        SeatInventory inventory = SeatInventory.restore(
                TripReference.of(2001L),
                20,
                7,
                13L,
                INITIALIZED_AT,
                INITIALIZED_AT.plusSeconds(300)
        );

        assertThat(inventory.totalSeats()).isEqualTo(20);
        assertThat(inventory.availableSeats()).isEqualTo(7);
        assertThat(inventory.version()).isEqualTo(13L);
    }

    @Test
    void shouldRejectInvalidInventoryState() {
        assertThatThrownBy(
                () -> SeatInventory.initialize(
                        TripReference.of(2001L),
                        0,
                        INITIALIZED_AT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalSeats must be positive");

        assertThatThrownBy(
                () -> SeatInventory.restore(
                        TripReference.of(2001L),
                        20,
                        21,
                        0L,
                        INITIALIZED_AT,
                        INITIALIZED_AT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "availableSeats must be between 0 and totalSeats"
                );
    }

    private SeatInventory inventoryWithTwoSeats() {
        return SeatInventory.initialize(
                TripReference.of(2001L),
                2,
                INITIALIZED_AT
        );
    }
}
