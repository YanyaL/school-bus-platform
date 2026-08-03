package com.schoolbus.transport.domain.trip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusTripTest {

    private static final Instant SCHEDULED_AT =
            Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant DEPARTURE_AT =
            Instant.parse("2026-08-04T08:00:00Z");
    private static final Instant ARRIVAL_AT =
            Instant.parse("2026-08-04T09:00:00Z");

    @Test
    void shouldScheduleTripWithAllSeatsAvailable() {
        BusTrip trip = scheduledTrip(45);

        assertThat(trip.tripId()).isEqualTo(TripId.of(1001L));
        assertThat(trip.routeId()).isEqualTo(RouteId.of(2001L));
        assertThat(trip.departureAt()).isEqualTo(DEPARTURE_AT);
        assertThat(trip.arrivalAt()).isEqualTo(ARRIVAL_AT);
        assertThat(trip.status()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(trip.seatCapacity())
                .isEqualTo(SeatCapacity.full(45));
        assertThat(trip.version()).isZero();
        assertThat(trip.createdAt()).isEqualTo(SCHEDULED_AT);
        assertThat(trip.updatedAt()).isEqualTo(SCHEDULED_AT);
        assertThat(trip.canReserve()).isTrue();
    }

    @Test
    void shouldRejectTripWhoseArrivalIsNotAfterDeparture() {
        assertThatThrownBy(
                () -> BusTrip.schedule(
                        TripId.of(1001L),
                        RouteId.of(2001L),
                        DEPARTURE_AT,
                        DEPARTURE_AT,
                        45,
                        SCHEDULED_AT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "arrivalAt must be after departureAt"
                );
    }

    @Test
    void shouldReserveAndReleaseSeat() {
        BusTrip trip = scheduledTrip(2);
        Instant reservedAt = SCHEDULED_AT.plusSeconds(60);
        Instant releasedAt = SCHEDULED_AT.plusSeconds(120);

        trip.reserveSeat(reservedAt);

        assertThat(trip.seatCapacity().availableSeats())
                .isEqualTo(1);
        assertThat(trip.version()).isEqualTo(1L);
        assertThat(trip.updatedAt()).isEqualTo(reservedAt);

        trip.releaseSeat(releasedAt);

        assertThat(trip.seatCapacity().availableSeats())
                .isEqualTo(2);
        assertThat(trip.version()).isEqualTo(2L);
        assertThat(trip.updatedAt()).isEqualTo(releasedAt);
    }

    @Test
    void shouldRejectReservationWhenTripIsFull() {
        BusTrip trip = scheduledTrip(1);
        trip.reserveSeat(SCHEDULED_AT.plusSeconds(60));

        assertThatThrownBy(
                () -> trip.reserveSeat(
                        SCHEDULED_AT.plusSeconds(120)
                )
        ).isInstanceOf(NoAvailableSeatException.class);

        assertThat(trip.seatCapacity().availableSeats()).isZero();
        assertThat(trip.version()).isEqualTo(1L);
    }

    @Test
    void shouldAllowReservationDuringBoarding() {
        BusTrip trip = scheduledTrip(2);
        trip.startBoarding(SCHEDULED_AT.plusSeconds(60));

        trip.reserveSeat(SCHEDULED_AT.plusSeconds(120));

        assertThat(trip.status()).isEqualTo(TripStatus.BOARDING);
        assertThat(trip.seatCapacity().availableSeats())
                .isEqualTo(1);
        assertThat(trip.canReserve()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(
            value = TripStatus.class,
            names = {"DEPARTED", "CANCELLED", "ARRIVED"}
    )
    void shouldRejectSeatOperationAfterTripCloses(
            TripStatus status
    ) {
        BusTrip trip = restoredTrip(status, new SeatCapacity(2, 1));

        assertThatThrownBy(
                () -> trip.reserveSeat(
                        SCHEDULED_AT.plusSeconds(60)
                )
        )
                .isInstanceOf(
                        TripSeatOperationNotAllowedException.class
                )
                .hasMessageContaining(status.name());
        assertThat(trip.canReserve()).isFalse();
    }

    @Test
    void shouldFollowValidTripLifecycle() {
        BusTrip trip = scheduledTrip(45);

        trip.startBoarding(SCHEDULED_AT.plusSeconds(60));
        assertThat(trip.status()).isEqualTo(TripStatus.BOARDING);

        trip.depart(SCHEDULED_AT.plusSeconds(120));
        assertThat(trip.status()).isEqualTo(TripStatus.DEPARTED);

        trip.arrive(SCHEDULED_AT.plusSeconds(180));
        assertThat(trip.status()).isEqualTo(TripStatus.ARRIVED);
        assertThat(trip.version()).isEqualTo(3L);
    }

    @Test
    void shouldCancelScheduledOrBoardingTrip() {
        BusTrip scheduled = scheduledTrip(45);
        scheduled.cancel(SCHEDULED_AT.plusSeconds(60));
        assertThat(scheduled.status())
                .isEqualTo(TripStatus.CANCELLED);

        BusTrip boarding = scheduledTrip(45);
        boarding.startBoarding(SCHEDULED_AT.plusSeconds(60));
        boarding.cancel(SCHEDULED_AT.plusSeconds(120));
        assertThat(boarding.status())
                .isEqualTo(TripStatus.CANCELLED);
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        BusTrip trip = scheduledTrip(45);

        assertThatThrownBy(
                () -> trip.arrive(
                        SCHEDULED_AT.plusSeconds(60)
                )
        )
                .isInstanceOf(
                        InvalidTripStateTransitionException.class
                )
                .hasMessage(
                        "cannot change trip status from SCHEDULED to ARRIVED"
                );

        assertThat(trip.status()).isEqualTo(TripStatus.SCHEDULED);
        assertThat(trip.version()).isZero();
    }

    @Test
    void shouldKeepTerminalStatusImmutable() {
        BusTrip trip = scheduledTrip(45);
        trip.cancel(SCHEDULED_AT.plusSeconds(60));

        assertThatThrownBy(
                () -> trip.startBoarding(
                        SCHEDULED_AT.plusSeconds(120)
                )
        ).isInstanceOf(
                InvalidTripStateTransitionException.class
        );

        assertThat(trip.status()).isEqualTo(TripStatus.CANCELLED);
        assertThat(trip.version()).isEqualTo(1L);
    }

    @Test
    void shouldRejectOutOfOrderMutationWithoutChangingTrip() {
        BusTrip trip = scheduledTrip(2);
        Instant firstChangeAt = SCHEDULED_AT.plusSeconds(120);
        trip.reserveSeat(firstChangeAt);

        assertThatThrownBy(
                () -> trip.releaseSeat(
                        SCHEDULED_AT.plusSeconds(60)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "changedAt must not be before updatedAt"
                );

        assertThat(trip.seatCapacity().availableSeats())
                .isEqualTo(1);
        assertThat(trip.version()).isEqualTo(1L);
        assertThat(trip.updatedAt()).isEqualTo(firstChangeAt);
    }

    @Test
    void shouldRestorePersistedTrip() {
        BusTrip trip = BusTrip.restore(
                TripId.of(1001L),
                RouteId.of(2001L),
                DEPARTURE_AT,
                ARRIVAL_AT,
                TripStatus.BOARDING,
                new SeatCapacity(45, 12),
                7L,
                SCHEDULED_AT,
                SCHEDULED_AT.plusSeconds(300)
        );

        assertThat(trip.status()).isEqualTo(TripStatus.BOARDING);
        assertThat(trip.seatCapacity().availableSeats())
                .isEqualTo(12);
        assertThat(trip.version()).isEqualTo(7L);
    }

    private BusTrip scheduledTrip(int totalSeats) {
        return BusTrip.schedule(
                TripId.of(1001L),
                RouteId.of(2001L),
                DEPARTURE_AT,
                ARRIVAL_AT,
                totalSeats,
                SCHEDULED_AT
        );
    }

    private BusTrip restoredTrip(
            TripStatus status,
            SeatCapacity capacity
    ) {
        return BusTrip.restore(
                TripId.of(1001L),
                RouteId.of(2001L),
                DEPARTURE_AT,
                ARRIVAL_AT,
                status,
                capacity,
                0L,
                SCHEDULED_AT,
                SCHEDULED_AT
        );
    }
}
