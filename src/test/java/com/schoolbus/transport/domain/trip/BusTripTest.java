package com.schoolbus.transport.domain.trip;

import com.schoolbus.transport.domain.vehicle.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusTripTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant BOOKING_DEADLINE =
            Instant.parse("2026-08-04T07:30:00Z");
    private static final Instant DEPARTURE_TIME =
            Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void shouldCreateDraftTrip() {
        BusTrip trip = draftTrip();

        assertThat(trip.tripId()).isEqualTo(TripId.of(1001L));
        assertThat(trip.vehicleId())
                .isEqualTo(VehicleId.of(3001L));
        assertThat(trip.routeId()).isEqualTo(RouteId.of(2001L));
        assertThat(trip.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(trip.price()).isEqualTo(Money.of("5.00"));
        assertThat(trip.version()).isZero();
    }

    @Test
    void shouldRejectInvalidBookingDeadline() {
        assertThatThrownBy(
                () -> BusTrip.draft(
                        TripId.of(1001L),
                        tripNumber(),
                        VehicleId.of(3001L),
                        RouteId.of(2001L),
                        DEPARTURE_TIME,
                        DEPARTURE_TIME,
                        Money.of("5.00"),
                        CREATED_AT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "bookingDeadline must be before departureTime"
                );
    }

    @Test
    void shouldFollowCompleteTripLifecycle() {
        BusTrip trip = draftTrip();

        trip.openForBooking(CREATED_AT.plusSeconds(60));
        assertThat(trip.status())
                .isEqualTo(TripStatus.OPEN_FOR_BOOKING);
        assertThat(trip.canBookAt(CREATED_AT.plusSeconds(120)))
                .isTrue();

        trip.closeBooking(CREATED_AT.plusSeconds(180));
        trip.depart(CREATED_AT.plusSeconds(240));
        trip.complete(CREATED_AT.plusSeconds(300));

        assertThat(trip.status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(trip.version()).isEqualTo(4L);
        assertThat(trip.updatedAt())
                .isEqualTo(CREATED_AT.plusSeconds(300));
    }

    @Test
    void shouldStopAcceptingBookingsAtDeadline() {
        BusTrip trip = draftTrip();
        trip.openForBooking(CREATED_AT.plusSeconds(60));

        assertThat(trip.canBookAt(BOOKING_DEADLINE.minusMillis(1)))
                .isTrue();
        assertThat(trip.canBookAt(BOOKING_DEADLINE)).isFalse();
        assertThat(trip.canBookAt(BOOKING_DEADLINE.plusMillis(1)))
                .isFalse();
    }

    @Test
    void shouldCancelTripBeforeDeparture() {
        BusTrip trip = draftTrip();
        trip.openForBooking(CREATED_AT.plusSeconds(60));
        trip.closeBooking(CREATED_AT.plusSeconds(120));

        trip.cancel(CREATED_AT.plusSeconds(180));

        assertThat(trip.status()).isEqualTo(TripStatus.CANCELLED);
        assertThat(trip.canBookAt(CREATED_AT.plusSeconds(240)))
                .isFalse();
    }

    @Test
    void shouldRejectInvalidStateTransitionWithoutMutation() {
        BusTrip trip = draftTrip();

        assertThatThrownBy(
                () -> trip.depart(CREATED_AT.plusSeconds(60))
        )
                .isInstanceOf(
                        InvalidTripStateTransitionException.class
                )
                .hasMessage(
                        "cannot change trip status from DRAFT to DEPARTED"
                );

        assertThat(trip.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(trip.version()).isZero();
    }

    @Test
    void shouldRejectOutOfOrderMutationWithoutMutation() {
        BusTrip trip = draftTrip();
        Instant openedAt = CREATED_AT.plusSeconds(120);
        trip.openForBooking(openedAt);

        assertThatThrownBy(
                () -> trip.closeBooking(
                        CREATED_AT.plusSeconds(60)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "changedAt must not be before updatedAt"
                );

        assertThat(trip.status())
                .isEqualTo(TripStatus.OPEN_FOR_BOOKING);
        assertThat(trip.version()).isEqualTo(1L);
        assertThat(trip.updatedAt()).isEqualTo(openedAt);
    }

    @Test
    void shouldRestorePersistedTrip() {
        BusTrip trip = BusTrip.restore(
                TripId.of(1001L),
                tripNumber(),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                Money.of("5.00"),
                TripStatus.OPEN_FOR_BOOKING,
                3L,
                CREATED_AT,
                CREATED_AT.plusSeconds(300)
        );

        assertThat(trip.status())
                .isEqualTo(TripStatus.OPEN_FOR_BOOKING);
        assertThat(trip.version()).isEqualTo(3L);
    }

    private BusTrip draftTrip() {
        return BusTrip.draft(
                TripId.of(1001L),
                tripNumber(),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                Money.of("5.00"),
                CREATED_AT
        );
    }

    private TripNumber tripNumber() {
        return TripNumber.of(
                "11111111-1111-1111-1111-111111111111"
        );
    }
}
