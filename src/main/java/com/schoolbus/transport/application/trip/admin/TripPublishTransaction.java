package com.schoolbus.transport.application.trip.admin;

import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.application.trip.TripAvailabilityChangedEvent;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.RouteId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.infrastructure.persistence.route.RouteReferenceDataObject;
import com.schoolbus.transport.infrastructure.persistence.route.RouteReferenceMapper;
import com.schoolbus.transport.infrastructure.persistence.seat.TripSeatInitializationMapper;
import com.schoolbus.transport.infrastructure.persistence.vehicle.VehicleReferenceDataObject;
import com.schoolbus.transport.infrastructure.persistence.vehicle.VehicleReferenceMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class TripPublishTransaction {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final BusTripRepository tripRepository;
    private final VehicleReferenceMapper vehicleReferenceMapper;
    private final RouteReferenceMapper routeReferenceMapper;
    private final TripSeatInitializationMapper tripSeatInitializationMapper;
    private final SeatInventoryRepository seatInventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TripPublishTransaction(
            BusTripRepository tripRepository,
            VehicleReferenceMapper vehicleReferenceMapper,
            RouteReferenceMapper routeReferenceMapper,
            TripSeatInitializationMapper tripSeatInitializationMapper,
            SeatInventoryRepository seatInventoryRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.tripRepository = Objects.requireNonNull(
                tripRepository,
                "tripRepository must not be null"
        );
        this.vehicleReferenceMapper = Objects.requireNonNull(
                vehicleReferenceMapper,
                "vehicleReferenceMapper must not be null"
        );
        this.routeReferenceMapper = Objects.requireNonNull(
                routeReferenceMapper,
                "routeReferenceMapper must not be null"
        );
        this.tripSeatInitializationMapper = Objects.requireNonNull(
                tripSeatInitializationMapper,
                "tripSeatInitializationMapper must not be null"
        );
        this.seatInventoryRepository = Objects.requireNonNull(
                seatInventoryRepository,
                "seatInventoryRepository must not be null"
        );
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "eventPublisher must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public TripAdminView publish(PublishTripCommand command) {
        PublishTripCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        Instant now = clock.instant();
        BusTrip trip = tripRepository
                .findByTripNumber(
                        TripNumber.of(validatedCommand.tripNumber())
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_NOT_FOUND
                ));

        if (trip.version() != validatedCommand.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (trip.status() != TripStatus.DRAFT) {
            throw new BusinessException(ErrorCode.TRIP_NOT_PUBLISHABLE);
        }
        if (!trip.bookingDeadline().isAfter(now)) {
            throw new BusinessException(ErrorCode.TRIP_NOT_PUBLISHABLE);
        }
        if (!trip.departureTime().isAfter(trip.bookingDeadline())) {
            throw new BusinessException(ErrorCode.TRIP_NOT_PUBLISHABLE);
        }

        VehicleReferenceDataObject vehicle = vehicleReferenceMapper
                .selectById(trip.vehicleId().value());
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND);
        }
        if (!"ENABLED".equals(vehicle.getStatus())) {
            throw new BusinessException(ErrorCode.VEHICLE_DISABLED);
        }

        RouteReferenceDataObject route = routeReferenceMapper
                .selectById(trip.routeId().value());
        if (route == null) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_FOUND);
        }
        if (!"ENABLED".equals(route.getStatus())) {
            throw new BusinessException(ErrorCode.ROUTE_DISABLED);
        }

        if (tripRepository.existsActiveTripForVehicleDeparture(
                trip.vehicleId(),
                trip.tripId(),
                trip.departureTime()
        )) {
            throw new BusinessException(ErrorCode.VEHICLE_SCHEDULE_CONFLICT);
        }

        long tripId = trip.tripId().value();
        if (tripSeatInitializationMapper.countByTripId(tripId) > 0) {
            throw new BusinessException(ErrorCode.TRIP_NOT_PUBLISHABLE);
        }
        if (seatInventoryRepository
                .findByTripReference(TripReference.of(tripId))
                .isPresent()) {
            throw new BusinessException(ErrorCode.TRIP_NOT_PUBLISHABLE);
        }

        List<String> seatNumbers = resolveSeatNumbers(vehicle);
        LocalDateTime createdAt = LocalDateTime.ofInstant(now, DATABASE_ZONE);
        int insertedSeats = tripSeatInitializationMapper.insertSeats(
                tripId,
                seatNumbers,
                createdAt
        );
        if (insertedSeats != seatNumbers.size()) {
            throw new IllegalStateException(
                    "failed to initialize trip seats"
            );
        }

        seatInventoryRepository.save(
                SeatInventory.initialize(
                        TripReference.of(tripId),
                        seatNumbers.size(),
                        now
                )
        );

        trip.openForBooking(now);
        try {
            tripRepository.save(trip);
        } catch (OptimisticLockingFailureException exception) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        eventPublisher.publishEvent(
                new TripAvailabilityChangedEvent(now)
        );
        return TripAdminView.from(trip);
    }

    private List<String> resolveSeatNumbers(
            VehicleReferenceDataObject vehicle
    ) {
        List<String> templateSeats = vehicleReferenceMapper
                .selectSeatNumbersByVehicleId(vehicle.getId());
        if (!templateSeats.isEmpty()) {
            return List.copyOf(templateSeats);
        }
        List<String> generatedSeats = new ArrayList<>(vehicle.getSeatCount());
        for (int seat = 1; seat <= vehicle.getSeatCount(); seat++) {
            generatedSeats.add(String.format("%02d", seat));
        }
        return generatedSeats;
    }
}
