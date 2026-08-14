package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.application.route.RouteNotFoundException;
import com.schoolbus.transport.application.vehicle.VehicleNotFoundException;
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
import com.schoolbus.transport.domain.trip.TripIdGenerator;
import com.schoolbus.transport.domain.trip.TripNumber;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripManagementApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-14T00:00:00Z");
    private static final Instant DEPARTURE_TIME =
            Instant.parse("2026-08-15T08:00:00Z");
    private static final Instant BOOKING_DEADLINE =
            Instant.parse("2026-08-15T07:30:00Z");

    @Mock
    private BusTripRepository tripRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private TripIdGenerator tripIdGenerator;

    private TripManagementApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TripManagementApplicationService(
                tripRepository,
                vehicleRepository,
                routeRepository,
                tripIdGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreateDraftAndCheckWholeScheduleInterval() {
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ENABLED)));
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(route(RouteStatus.ENABLED)));
        when(tripRepository.existsVehicleScheduleConflict(
                VehicleId.of(3001L),
                DEPARTURE_TIME,
                DEPARTURE_TIME.plusSeconds(40 * 60L)
        )).thenReturn(false);
        when(tripIdGenerator.nextId()).thenReturn(TripId.of(5001L));
        when(tripRepository.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        AdminTripView result = service.createDraft(command());

        assertThat(result.tripId()).isEqualTo(5001L);
        assertThat(result.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(result.price()).isEqualByComparingTo("5.00");
        verify(vehicleRepository).findByIdForUpdate(VehicleId.of(3001L));
        verify(tripRepository).save(any(BusTrip.class));
    }

    @Test
    void shouldRejectPastDepartureBeforeLockingVehicle() {
        CreateTripDraftCommand invalid = new CreateTripDraftCommand(
                3001L,
                2001L,
                NOW.minusSeconds(1),
                NOW.minusSeconds(60),
                new BigDecimal("5.00")
        );

        assertThatThrownBy(() -> service.createDraft(invalid))
                .isInstanceOf(InvalidTripScheduleException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.INVALID_TRIP_SCHEDULE);
        verify(vehicleRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void shouldRejectDeadlineAfterDeparture() {
        CreateTripDraftCommand invalid = new CreateTripDraftCommand(
                3001L,
                2001L,
                DEPARTURE_TIME,
                DEPARTURE_TIME,
                new BigDecimal("5.00")
        );

        assertThatThrownBy(() -> service.createDraft(invalid))
                .isInstanceOf(InvalidTripScheduleException.class);
    }

    @Test
    void shouldRejectMissingVehicle() {
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraft(command()))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void shouldRejectDisabledVehicle() {
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.DISABLED)));

        assertThatThrownBy(() -> service.createDraft(command()))
                .isInstanceOf(VehicleNotAvailableForTripException.class);
    }

    @Test
    void shouldRejectMissingRoute() {
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ENABLED)));
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDraft(command()))
                .isInstanceOf(RouteNotFoundException.class);
    }

    @Test
    void shouldRejectDisabledRoute() {
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ENABLED)));
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(route(RouteStatus.DISABLED)));

        assertThatThrownBy(() -> service.createDraft(command()))
                .isInstanceOf(RouteNotAvailableForTripException.class);
    }

    @Test
    void shouldRejectOverlappingVehicleSchedule() {
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ENABLED)));
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(route(RouteStatus.ENABLED)));
        when(tripRepository.existsVehicleScheduleConflict(
                any(),
                any(),
                any()
        )).thenReturn(true);

        assertThatThrownBy(() -> service.createDraft(command()))
                .isInstanceOf(VehicleScheduleConflictException.class);
        verify(tripRepository, never()).save(any());
    }

    @Test
    void shouldMapExactDepartureUniqueConstraintToScheduleConflict() {
        when(vehicleRepository.findByIdForUpdate(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle(VehicleStatus.ENABLED)));
        when(routeRepository.findById(RouteId.of(2001L)))
                .thenReturn(Optional.of(route(RouteStatus.ENABLED)));
        when(tripIdGenerator.nextId()).thenReturn(TripId.of(5001L));
        when(tripRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "uk_transport_trip_vehicle_departure"
                ));

        assertThatThrownBy(() -> service.createDraft(command()))
                .isInstanceOf(VehicleScheduleConflictException.class);
    }

    @Test
    void shouldFindAndListAdminTrips() {
        BusTrip trip = trip();
        when(tripRepository.findById(TripId.of(5001L)))
                .thenReturn(Optional.of(trip));
        when(tripRepository.findAll(TripStatus.DRAFT, 20, 20))
                .thenReturn(List.of(trip));

        assertThat(service.findById(5001L).tripNumber())
                .isEqualTo(trip.tripNumber().toString());
        assertThat(service.listTrips(TripStatus.DRAFT, 1, 20))
                .hasSize(1);
    }

    @Test
    void shouldRejectMissingTrip() {
        when(tripRepository.findById(TripId.of(9999L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9999L))
                .isInstanceOf(TripNotFoundException.class);
    }

    private CreateTripDraftCommand command() {
        return new CreateTripDraftCommand(
                3001L,
                2001L,
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                new BigDecimal("5.00")
        );
    }

    private Vehicle vehicle(VehicleStatus status) {
        return Vehicle.restore(
                VehicleId.of(3001L),
                VehicleNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                LicensePlate.of("QLD123"),
                40,
                status,
                0L,
                NOW,
                NOW
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
                NOW,
                NOW
        );
    }

    private BusTrip trip() {
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
                NOW
        );
    }
}
