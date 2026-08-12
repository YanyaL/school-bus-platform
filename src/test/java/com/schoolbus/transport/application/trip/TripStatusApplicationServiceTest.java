package com.schoolbus.transport.application.trip;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripStatusApplicationServiceTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant BOOKING_DEADLINE =
            Instant.parse("2026-08-04T07:30:00Z");
    private static final Instant NOW =
            Instant.parse("2026-08-04T08:00:00Z");
    private static final Instant DEPARTURE_TIME = NOW;

    @Mock
    private BusTripRepository busTripRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TripStatusApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TripStatusApplicationService(
                busTripRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                eventPublisher
        );
    }

    @Test
    void shouldCloseDueBookingsAndDepartDueTrips() {
        BusTrip openTrip = openTrip(1001L);
        BusTrip closedTrip = closedTrip(1002L);
        when(busTripRepository.findDueOpenTripsForClosing(
                NOW,
                TripStatusApplicationService.BATCH_SIZE
        )).thenReturn(List.of(openTrip));
        when(busTripRepository.findDueClosedTripsForDeparture(
                NOW,
                TripStatusApplicationService.BATCH_SIZE
        )).thenReturn(List.of(closedTrip));
        when(busTripRepository.save(openTrip)).thenReturn(openTrip);
        when(busTripRepository.save(closedTrip)).thenReturn(closedTrip);

        TripStatusUpdateResult result =
                service.updateDueTripStatuses();

        assertThat(openTrip.status()).isEqualTo(TripStatus.CLOSED);
        assertThat(closedTrip.status())
                .isEqualTo(TripStatus.DEPARTED);
        assertThat(result).isEqualTo(
                new TripStatusUpdateResult(1, 1, 0)
        );
        verify(busTripRepository).save(openTrip);
        verify(busTripRepository).save(closedTrip);
        verify(eventPublisher).publishEvent(
                new TripAvailabilityChangedEvent(NOW)
        );
    }

    @Test
    void shouldContinueWhenAnotherWorkerWinsOptimisticLock() {
        BusTrip openTrip = openTrip(1001L);
        when(busTripRepository.findDueOpenTripsForClosing(
                NOW,
                TripStatusApplicationService.BATCH_SIZE
        )).thenReturn(List.of(openTrip));
        when(busTripRepository.findDueClosedTripsForDeparture(
                NOW,
                TripStatusApplicationService.BATCH_SIZE
        )).thenReturn(List.of());
        when(busTripRepository.save(openTrip)).thenThrow(
                new OptimisticLockingFailureException("conflict")
        );

        TripStatusUpdateResult result =
                service.updateDueTripStatuses();

        assertThat(result).isEqualTo(
                new TripStatusUpdateResult(0, 0, 1)
        );
        verifyNoInteractions(eventPublisher);
    }

    private BusTrip openTrip(long id) {
        BusTrip trip = draftTrip(id);
        trip.openForBooking(CREATED_AT.plusSeconds(60));
        return trip;
    }

    private BusTrip closedTrip(long id) {
        BusTrip trip = openTrip(id);
        trip.closeBooking(BOOKING_DEADLINE);
        return trip;
    }

    private BusTrip draftTrip(long id) {
        return BusTrip.draft(
                TripId.of(id),
                TripNumber.generate(),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                Money.of("5.00"),
                CREATED_AT
        );
    }
}
