package com.schoolbus.transport.application.trip;

import com.schoolbus.transport.application.route.RouteNotFoundException;
import com.schoolbus.transport.application.vehicle.VehicleNotFoundException;
import com.schoolbus.transport.domain.route.Route;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.route.RouteRepository;
import com.schoolbus.transport.domain.route.RouteStatus;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripIdGenerator;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.vehicle.Vehicle;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import com.schoolbus.transport.domain.vehicle.VehicleRepository;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class TripManagementApplicationService {

    private final BusTripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;
    private final TripIdGenerator tripIdGenerator;
    private final Clock clock;

    public TripManagementApplicationService(
            BusTripRepository tripRepository,
            VehicleRepository vehicleRepository,
            RouteRepository routeRepository,
            TripIdGenerator tripIdGenerator,
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
        this.tripIdGenerator = Objects.requireNonNull(
                tripIdGenerator,
                "tripIdGenerator must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public AdminTripView createDraft(CreateTripDraftCommand command) {
        CreateTripDraftCommand validated = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        Instant now = clock.instant();
        validateSchedule(validated, now);

        VehicleId vehicleId = VehicleId.of(validated.vehicleId());
        Vehicle vehicle = vehicleRepository
                .findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(
                        validated.vehicleId()
                ));
        if (vehicle.status() != VehicleStatus.ENABLED) {
            throw new VehicleNotAvailableForTripException(
                    validated.vehicleId()
            );
        }

        Route route = routeRepository
                .findById(RouteId.of(validated.routeId()))
                .orElseThrow(() -> new RouteNotFoundException(
                        validated.routeId()
                ));
        if (route.status() != RouteStatus.ENABLED) {
            throw new RouteNotAvailableForTripException(
                    validated.routeId()
            );
        }

        Instant arrivalTime = calculateArrivalTime(
                validated.departureTime(),
                route.estimatedDurationMinutes()
        );
        if (tripRepository.existsVehicleScheduleConflict(
                vehicleId,
                validated.departureTime(),
                arrivalTime
        )) {
            throw new VehicleScheduleConflictException(
                    validated.vehicleId()
            );
        }

        BusTrip trip;
        try {
            trip = BusTrip.draft(
                    tripIdGenerator.nextId(),
                    TripNumber.generate(),
                    vehicleId,
                    route.id(),
                    validated.departureTime(),
                    validated.bookingDeadline(),
                    new Money(validated.price()),
                    now
            );
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new InvalidTripScheduleException(
                    exception.getMessage()
            );
        }

        try {
            return toView(tripRepository.save(trip));
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(
                    exception,
                    "uk_transport_trip_vehicle_departure"
            )) {
                throw new VehicleScheduleConflictException(
                        validated.vehicleId()
                );
            }
            throw exception;
        }
    }

    public AdminTripView findById(long tripId) {
        return tripRepository
                .findById(TripId.of(tripId))
                .map(this::toView)
                .orElseThrow(() -> new TripNotFoundException(tripId));
    }

    public List<AdminTripView> listTrips(
            TripStatus status,
            int page,
            int size
    ) {
        int offset = page * size;
        return tripRepository
                .findAll(status, offset, size)
                .stream()
                .map(this::toView)
                .toList();
    }

    private void validateSchedule(
            CreateTripDraftCommand command,
            Instant now
    ) {
        Instant departureTime = Objects.requireNonNull(
                command.departureTime(),
                "departureTime must not be null"
        );
        Instant bookingDeadline = Objects.requireNonNull(
                command.bookingDeadline(),
                "bookingDeadline must not be null"
        );
        Objects.requireNonNull(command.price(), "price must not be null");
        if (!departureTime.isAfter(now)) {
            throw new InvalidTripScheduleException(
                    "departureTime must be in the future"
            );
        }
        if (!bookingDeadline.isAfter(now)) {
            throw new InvalidTripScheduleException(
                    "bookingDeadline must be in the future"
            );
        }
        if (!bookingDeadline.isBefore(departureTime)) {
            throw new InvalidTripScheduleException(
                    "bookingDeadline must be before departureTime"
            );
        }
    }

    private Instant calculateArrivalTime(
            Instant departureTime,
            int durationMinutes
    ) {
        try {
            return departureTime.plus(
                    durationMinutes,
                    ChronoUnit.MINUTES
            );
        } catch (DateTimeException | ArithmeticException exception) {
            throw new InvalidTripScheduleException(
                    "trip arrivalTime exceeds the supported range"
            );
        }
    }

    private boolean containsConstraint(
            RuntimeException exception,
            String constraintName
    ) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private AdminTripView toView(BusTrip trip) {
        return new AdminTripView(
                trip.tripId().value(),
                trip.tripNumber().toString(),
                trip.vehicleId().value(),
                trip.routeId().value(),
                trip.departureTime(),
                trip.bookingDeadline(),
                trip.price().amount(),
                trip.status(),
                trip.version(),
                trip.createdAt(),
                trip.updatedAt()
        );
    }
}
