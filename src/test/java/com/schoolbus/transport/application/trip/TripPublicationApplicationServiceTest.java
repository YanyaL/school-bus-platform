package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.domain.route.Campus;
import com.schoolbus.transport.domain.route.Route;
import com.schoolbus.transport.domain.route.RouteCode;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.route.RouteNumber;
import com.schoolbus.transport.domain.route.RouteRepository;
import com.schoolbus.transport.domain.route.RouteStatus;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripSeatRepository;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.vehicle.LicensePlate;
import com.schoolbus.transport.domain.vehicle.Vehicle;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import com.schoolbus.transport.domain.vehicle.VehicleNumber;
import com.schoolbus.transport.domain.vehicle.VehicleRepository;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripPublicationApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-14T00:00:00Z");
    private static final Instant DEPARTURE_TIME =
            Instant.parse("2026-08-15T08:00:00Z");
    private static final Instant BOOKING_DEADLINE =
            Instant.parse("2026-08-15T07:30:00Z");
    private static final List<String> SEATS =
            List.of("1", "2", "3");

    @Mock
    private BusTripRepository tripRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private TripSeatRepository tripSeatRepository;

    @Mock
    private TripInventoryInitializationPort inventoryInitializer;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TripPublicationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TripPublicationApplicationService(
                tripRepository,
                vehicleRepository,
                routeRepository,
                tripSeatRepository,
                inventoryInitializer,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldPublishTripAndInitializeSeatsAndInventory() {
        BusTrip trip = preparePublishableTrip();
        when(tripRepository.save(trip)).thenReturn(trip);

        AdminTripView result = service.publish(
                new PublishTripCommand(5001L, 0L)
        );

        assertThat(result.status()).isEqualTo(
                TripStatus.OPEN_FOR_BOOKING
        );
        assertThat(result.version()).isEqualTo(1L);
        verify(tripRepository).save(trip);
        verify(tripSeatRepository).initializeSeats(
                TripId.of(5001L),
                SEATS,
                NOW
        );
        verify(inventoryInitializer).initialize(5001L, 3, NOW);
        verify(eventPublisher).publishEvent(
                new TripAvailabilityChangedEvent(NOW)
        );
    }

    @Test
    void shouldRejectStaleExpectedVersion() {
        BusTrip trip = draftTrip();
        when(tripRepository.findById(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.publish(
                new PublishTripCommand(5001L, 1L)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
        verifyNoInteractions(tripSeatRepository, inventoryInitializer);
    }

    @Test
    void shouldRejectAlreadyPublishedTrip() {
        BusTrip trip = draftTrip();
        trip.openForBooking(NOW.minusSeconds(60));
        when(tripRepository.findById(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.publish(
                new PublishTripCommand(5001L, 1L)
        )).isInstanceOf(TripNotPublishableException.class);
    }

    @Test
    void shouldRejectTripWhoseBookingDeadlineArrived() {
        BusTrip trip = expiredDraftTrip();
        when(tripRepository.findById(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> service.publish(
                new PublishTripCommand(5001L, 0L)
        )).isInstanceOf(TripNotPublishableException.class);
        verifyNoInteractions(tripSeatRepository, inventoryInitializer);
    }

    @Test
    void shouldRejectDisabledVehicle() {
        BusTrip trip = draftTrip();
        when(tripRepository.findById(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.DISABLED)));

        assertThatThrownBy(() -> service.publish(
                new PublishTripCommand(5001L, 0L)
        )).isInstanceOf(VehicleNotAvailableForTripException.class);
    }

    @Test
    void shouldRejectDisabledRoute() {
        BusTrip trip = draftTrip();
        when(tripRepository.findById(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ENABLED)));
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(route(RouteStatus.DISABLED)));

        assertThatThrownBy(() -> service.publish(
                new PublishTripCommand(5001L, 0L)
        )).isInstanceOf(RouteNotAvailableForTripException.class);
    }

    @Test
    void shouldRejectInconsistentSeatTemplate() {
        BusTrip trip = draftTrip();
        when(tripRepository.findById(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ENABLED)));
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(route(RouteStatus.ENABLED)));
        when(vehicleRepository.findSeatNumbersByVehicleId(
                VehicleId.of(3001L)
        )).thenReturn(List.of("1", "2"));

        assertThatThrownBy(() -> service.publish(
                new PublishTripCommand(5001L, 0L)
        )).isInstanceOf(TripSeatTemplateInvalidException.class);
        verify(tripRepository, never()).save(any());
    }

    @Test
    void shouldMapConcurrentStatusUpdateToVersionConflict() {
        BusTrip trip = preparePublishableTrip();
        when(tripRepository.save(trip)).thenThrow(
                new OptimisticLockingFailureException("conflict")
        );

        assertThatThrownBy(() -> service.publish(
                new PublishTripCommand(5001L, 0L)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
        verifyNoInteractions(tripSeatRepository, inventoryInitializer);
    }

    @Test
    void shouldMapDuplicateInitializationToNotPublishable() {
        BusTrip trip = preparePublishableTrip();
        when(tripRepository.save(trip)).thenReturn(trip);
        org.mockito.Mockito.doThrow(
                new DataIntegrityViolationException("duplicate")
        ).when(tripSeatRepository).initializeSeats(
                TripId.of(5001L),
                SEATS,
                NOW
        );

        assertThatThrownBy(() -> service.publish(
                new PublishTripCommand(5001L, 0L)
        )).isInstanceOf(TripNotPublishableException.class);
        verifyNoInteractions(inventoryInitializer, eventPublisher);
    }

    private BusTrip preparePublishableTrip() {
        BusTrip trip = draftTrip();
        when(tripRepository.findById(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ENABLED)));
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(route(RouteStatus.ENABLED)));
        when(vehicleRepository.findSeatNumbersByVehicleId(
                VehicleId.of(3001L)
        )).thenReturn(SEATS);
        return trip;
    }

    private BusTrip draftTrip() {
        return BusTrip.draft(
                TripId.of(5001L),
                TripNumber.of(
                        "33333333-3333-3333-3333-333333333333"
                ),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                Money.of("5.00"),
                NOW.minusSeconds(3600)
        );
    }

    private BusTrip expiredDraftTrip() {
        return BusTrip.draft(
                TripId.of(5001L),
                TripNumber.of(
                        "33333333-3333-3333-3333-333333333333"
                ),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                NOW.plusSeconds(3600),
                NOW,
                Money.of("5.00"),
                NOW.minusSeconds(3600)
        );
    }

    private Vehicle vehicle(VehicleStatus status) {
        return Vehicle.restore(
                VehicleId.of(3001L),
                VehicleNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                LicensePlate.of("QLD123"),
                3,
                status,
                0L,
                NOW.minusSeconds(3600),
                NOW.minusSeconds(3600)
        );
    }

    private Route route(RouteStatus status) {
        return Route.restore(
                RouteId.of(2001L),
                RouteNumber.of(
                        "22222222-2222-2222-2222-222222222222"
                ),
                RouteCode.of("MAIN-EAST-01"),
                Campus.MAIN,
                Campus.EAST,
                40,
                status,
                0L,
                NOW.minusSeconds(3600),
                NOW.minusSeconds(3600)
        );
    }
}
