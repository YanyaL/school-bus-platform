package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripCancellationApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-14T00:00:00Z");

    @Mock
    private BusTripRepository tripRepository;

    @Mock
    private TripBookingStatePort bookingStatePort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TripCancellationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TripCancellationApplicationService(
                tripRepository,
                bookingStatePort,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCancelDraftWithoutCheckingBookings() {
        BusTrip trip = tripIn(TripStatus.DRAFT);
        when(tripRepository.findByIdForUpdate(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);

        AdminTripView result = service.cancel(
                new CancelTripCommand(5001L, 0L)
        );

        assertThat(result.status()).isEqualTo(TripStatus.CANCELLED);
        assertThat(result.version()).isEqualTo(1L);
        verifyNoInteractions(bookingStatePort);
        verify(eventPublisher).publishEvent(
                new TripAvailabilityChangedEvent(NOW)
        );
    }

    @Test
    void shouldCancelPublishedTripWithoutActiveBookings() {
        BusTrip trip = tripIn(TripStatus.OPEN_FOR_BOOKING);
        when(tripRepository.findByIdForUpdate(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(bookingStatePort.hasActiveBookings(5001L))
                .thenReturn(false);
        when(tripRepository.save(trip)).thenReturn(trip);

        AdminTripView result = service.cancel(
                new CancelTripCommand(5001L, 1L)
        );

        assertThat(result.status()).isEqualTo(TripStatus.CANCELLED);
        assertThat(result.version()).isEqualTo(2L);
        verify(bookingStatePort).hasActiveBookings(5001L);
    }

    @Test
    void shouldRejectPublishedTripWithActiveBookings() {
        BusTrip trip = tripIn(TripStatus.OPEN_FOR_BOOKING);
        when(tripRepository.findByIdForUpdate(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(bookingStatePort.hasActiveBookings(5001L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.cancel(
                new CancelTripCommand(5001L, 1L)
        )).isInstanceOf(TripHasActiveBookingsException.class);

        verify(tripRepository, never()).save(trip);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void shouldReturnAlreadyCancelledTripIdempotently() {
        BusTrip trip = tripIn(TripStatus.CANCELLED);
        when(tripRepository.findByIdForUpdate(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));

        AdminTripView result = service.cancel(
                new CancelTripCommand(5001L, 0L)
        );

        assertThat(result.status()).isEqualTo(TripStatus.CANCELLED);
        verify(tripRepository, never()).save(trip);
        verifyNoInteractions(bookingStatePort, eventPublisher);
    }

    @Test
    void shouldRejectDepartedTrip() {
        BusTrip trip = tripIn(TripStatus.DEPARTED);
        when(tripRepository.findByIdForUpdate(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.cancel(
                new CancelTripCommand(5001L, 3L)
        )).isInstanceOf(TripNotCancellableException.class);
    }

    @Test
    void shouldRejectStaleVersion() {
        BusTrip trip = tripIn(TripStatus.DRAFT);
        when(tripRepository.findByIdForUpdate(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.cancel(
                new CancelTripCommand(5001L, 1L)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
    }

    @Test
    void shouldMapConcurrentUpdateToVersionConflict() {
        BusTrip trip = tripIn(TripStatus.DRAFT);
        when(tripRepository.findByIdForUpdate(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenThrow(
                new OptimisticLockingFailureException("conflict")
        );

        assertThatThrownBy(() -> service.cancel(
                new CancelTripCommand(5001L, 0L)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
        verifyNoInteractions(eventPublisher);
    }

    private BusTrip tripIn(TripStatus status) {
        BusTrip trip = BusTrip.draft(
                TripId.of(5001L),
                TripNumber.of(
                        "33333333-3333-3333-3333-333333333333"
                ),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                NOW.plusSeconds(24 * 3600),
                NOW.plusSeconds(23 * 3600),
                Money.of("5.00"),
                NOW.minusSeconds(3600)
        );
        if (status == TripStatus.OPEN_FOR_BOOKING) {
            trip.openForBooking(NOW.minusSeconds(1800));
        } else if (status == TripStatus.CLOSED) {
            trip.openForBooking(NOW.minusSeconds(1800));
            trip.closeBooking(NOW.minusSeconds(1200));
        } else if (status == TripStatus.DEPARTED) {
            trip.openForBooking(NOW.minusSeconds(1800));
            trip.closeBooking(NOW.minusSeconds(1200));
            trip.depart(NOW.minusSeconds(600));
        } else if (status == TripStatus.CANCELLED) {
            trip.cancel(NOW.minusSeconds(1800));
        }
        return trip;
    }
}
