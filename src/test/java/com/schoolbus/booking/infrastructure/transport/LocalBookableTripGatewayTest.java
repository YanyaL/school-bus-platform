package com.schoolbus.booking.infrastructure.transport;

import com.schoolbus.booking.application.booking.BookableTripSnapshot;
import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalBookableTripGatewayTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant DEADLINE =
            Instant.parse("2026-08-08T01:00:00Z");
    private static final Instant DEPARTURE =
            Instant.parse("2026-08-08T02:00:00Z");

    @Mock
    private BusTripRepository busTripRepository;

    private LocalBookableTripGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new LocalBookableTripGateway(
                busTripRepository
        );
    }

    @Test
    void shouldTranslateTransportTripToBookingSnapshot() {
        BusTrip trip = draftTrip();
        trip.openForBooking(CREATED_AT.plusSeconds(60));
        when(busTripRepository.findByIdForShare(TripId.of(2001L)))
                .thenReturn(Optional.of(trip));

        BookableTripSnapshot snapshot = gateway
                .findByTripReference(TripReference.of(2001L))
                .orElseThrow();

        assertThat(snapshot.tripReference())
                .isEqualTo(TripReference.of(2001L));
        assertThat(snapshot.price())
                .isEqualTo(BookingAmount.of("5.50"));
        assertThat(snapshot.openForBooking()).isTrue();
        assertThat(snapshot.canBookAt(CREATED_AT.plusSeconds(120)))
                .isTrue();
    }

    @Test
    void shouldReturnEmptyWhenTransportTripDoesNotExist() {
        when(busTripRepository.findByIdForShare(TripId.of(2001L)))
                .thenReturn(Optional.empty());

        assertThat(
                gateway.findByTripReference(
                        TripReference.of(2001L)
                )
        ).isEmpty();
    }

    private BusTrip draftTrip() {
        return BusTrip.draft(
                TripId.of(2001L),
                TripNumber.of(
                        "22222222-2222-2222-2222-222222222222"
                ),
                VehicleId.of(3001L),
                RouteId.of(4001L),
                DEPARTURE,
                DEADLINE,
                Money.of("5.50"),
                CREATED_AT
        );
    }
}
