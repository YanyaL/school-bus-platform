package com.schoolbus.transport.domain.trip;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatCapacityTest {

    @Test
    void shouldCreateFullCapacity() {
        SeatCapacity capacity = SeatCapacity.full(45);

        assertThat(capacity.totalSeats()).isEqualTo(45);
        assertThat(capacity.availableSeats()).isEqualTo(45);
        assertThat(capacity.hasAvailableSeat()).isTrue();
    }

    @Test
    void shouldRejectInvalidCapacity() {
        assertThatThrownBy(() -> SeatCapacity.full(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalSeats must be positive");
        assertThatThrownBy(() -> new SeatCapacity(45, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "availableSeats must not be negative"
                );
        assertThatThrownBy(() -> new SeatCapacity(45, 46))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "availableSeats must not exceed totalSeats"
                );
    }

    @Test
    void shouldReturnNewCapacityWhenReservingSeat() {
        SeatCapacity original = SeatCapacity.full(2);

        SeatCapacity reserved = original.reserveOne();

        assertThat(original.availableSeats()).isEqualTo(2);
        assertThat(reserved.availableSeats()).isEqualTo(1);
    }

    @Test
    void shouldRejectReservationWhenNoSeatIsAvailable() {
        SeatCapacity capacity = new SeatCapacity(2, 0);

        assertThatThrownBy(capacity::reserveOne)
                .isInstanceOf(NoAvailableSeatException.class)
                .hasMessage("no available seat");
    }

    @Test
    void shouldReleaseReservedSeat() {
        SeatCapacity capacity = new SeatCapacity(2, 1);

        SeatCapacity released = capacity.releaseOne();

        assertThat(released.availableSeats()).isEqualTo(2);
    }

    @Test
    void shouldRejectReleaseBeyondTotalSeats() {
        SeatCapacity capacity = SeatCapacity.full(2);

        assertThatThrownBy(capacity::releaseOne)
                .isInstanceOf(
                        SeatCapacityExceededException.class
                )
                .hasMessage(
                        "available seats cannot exceed total seats"
                );
    }
}
