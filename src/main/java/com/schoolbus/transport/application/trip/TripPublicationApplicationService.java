package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.application.route.RouteNotFoundException;
import com.schoolbus.transport.application.vehicle.VehicleNotFoundException;
import com.schoolbus.transport.domain.route.Route;
import com.schoolbus.transport.domain.route.RouteRepository;
import com.schoolbus.transport.domain.route.RouteStatus;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripSeatRepository;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.vehicle.Vehicle;
import com.schoolbus.transport.domain.vehicle.VehicleRepository;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class TripPublicationApplicationService {

    private final BusTripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;
    private final TripSeatRepository tripSeatRepository;
    private final TripInventoryInitializationPort inventoryInitializer;
    private final TripPublicationOutboxPort publicationOutbox;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TripPublicationApplicationService(
            BusTripRepository tripRepository,
            VehicleRepository vehicleRepository,
            RouteRepository routeRepository,
            TripSeatRepository tripSeatRepository,
            TripInventoryInitializationPort inventoryInitializer,
            TripPublicationOutboxPort publicationOutbox,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.tripRepository = Objects.requireNonNull(
                tripRepository,
                "tripRepository must not be null"
        );
        this.vehicleRepository = Objects.requireNonNull(
                vehicleRepository,
                "vehicleRepository must not be null"
        );
        this.routeRepository = Objects.requireNonNull(
                routeRepository,
                "routeRepository must not be null"
        );
        this.tripSeatRepository = Objects.requireNonNull(
                tripSeatRepository,
                "tripSeatRepository must not be null"
        );
        this.inventoryInitializer = Objects.requireNonNull(
                inventoryInitializer,
                "inventoryInitializer must not be null"
        );
        this.publicationOutbox = Objects.requireNonNull(publicationOutbox, "publicationOutbox must not be null");
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "eventPublisher must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public AdminTripView publish(PublishTripCommand command) {
        PublishTripCommand validated = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        if (validated.expectedVersion() < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion must not be negative"
            );
        }

        BusTrip trip = tripRepository
                .findById(TripId.of(validated.tripId()))
                .orElseThrow(() -> new TripNotFoundException(
                        validated.tripId()
                ));
        if (trip.version() != validated.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (trip.status() != TripStatus.DRAFT) {
            throw new TripNotPublishableException(
                    validated.tripId(),
                    "only DRAFT trips can be published"
            );
        }

        Instant now = clock.instant();
        if (!now.isBefore(trip.bookingDeadline())) {
            throw new TripNotPublishableException(
                    validated.tripId(),
                    "booking deadline has already arrived"
            );
        }
        if (!now.isBefore(trip.departureTime())) {
            throw new TripNotPublishableException(
                    validated.tripId(),
                    "departure time has already arrived"
            );
        }

        Vehicle vehicle = vehicleRepository
                .findByIdForUpdate(trip.vehicleId())
                .orElseThrow(() -> new VehicleNotFoundException(
                        trip.vehicleId().value()
                ));
        if (vehicle.status() != VehicleStatus.ENABLED) {
            throw new VehicleNotAvailableForTripException(
                    vehicle.id().value()
            );
        }

        Route route = routeRepository
                .findById(trip.routeId())
                .orElseThrow(() -> new RouteNotFoundException(
                        trip.routeId().value()
                ));
        if (route.status() != RouteStatus.ENABLED) {
            throw new RouteNotAvailableForTripException(
                    route.id().value()
            );
        }

        List<String> seatNumbers = vehicleRepository
                .findSeatNumbersByVehicleId(vehicle.id());
        validateSeatTemplate(vehicle, seatNumbers);

        trip.openForBooking(now);
        try {
            tripRepository.save(trip);
        } catch (OptimisticLockingFailureException exception) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        try {
            tripSeatRepository.initializeSeats(
                    trip.tripId(),
                    seatNumbers,
                    now
            );
            inventoryInitializer.initialize(
                    trip.tripId().value(),
                    seatNumbers.size(),
                    now
            );
        } catch (DataIntegrityViolationException exception) {
            throw new TripNotPublishableException(
                    trip.tripId().value(),
                    "seat or inventory data has already been initialized"
            );
        }

        publicationOutbox.append(new TripPublishedEvent(trip.tripId().value(), trip.tripNumber().value(),
                trip.version(), seatNumbers, trip.price().amount(), trip.bookingDeadline(),
                trip.departureTime(), now));

        eventPublisher.publishEvent(
                new TripAvailabilityChangedEvent(now)
        );
        return AdminTripView.from(trip);
    }

    private void validateSeatTemplate(
            Vehicle vehicle,
            List<String> seatNumbers
    ) {
        if (seatNumbers == null
                || seatNumbers.size() != vehicle.seatCount()
                || seatNumbers.stream().anyMatch(
                        seatNumber -> seatNumber == null
                                || seatNumber.isBlank()
                )
                || new HashSet<>(seatNumbers).size()
                        != seatNumbers.size()) {
            throw new TripSeatTemplateInvalidException(
                    vehicle.id().value()
            );
        }
    }
}
