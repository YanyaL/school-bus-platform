package com.schoolbus.transport.application.trip.admin;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.RouteId;
import com.schoolbus.transport.domain.trip.TripIdGenerator;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.VehicleId;
import com.schoolbus.transport.infrastructure.persistence.route.RouteReferenceDataObject;
import com.schoolbus.transport.infrastructure.persistence.route.RouteReferenceMapper;
import com.schoolbus.transport.infrastructure.persistence.vehicle.VehicleReferenceDataObject;
import com.schoolbus.transport.infrastructure.persistence.vehicle.VehicleReferenceMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

@Service
@Profile("!test")
public class TripAdminApplicationService {

    private final BusTripRepository tripRepository;
    private final VehicleReferenceMapper vehicleReferenceMapper;
    private final RouteReferenceMapper routeReferenceMapper;
    private final TripIdGenerator tripIdGenerator;
    private final TripPublishTransaction publishTransaction;
    private final Clock clock;

    public TripAdminApplicationService(
            BusTripRepository tripRepository,
            VehicleReferenceMapper vehicleReferenceMapper,
            RouteReferenceMapper routeReferenceMapper,
            TripIdGenerator tripIdGenerator,
            TripPublishTransaction publishTransaction,
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
        this.tripIdGenerator = Objects.requireNonNull(
                tripIdGenerator,
                "tripIdGenerator must not be null"
        );
        this.publishTransaction = Objects.requireNonNull(
                publishTransaction,
                "publishTransaction must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public TripAdminView createDraftTrip(CreateDraftTripCommand command) {
        CreateDraftTripCommand validatedCommand = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        VehicleReferenceDataObject vehicle = vehicleReferenceMapper
                .selectByVehicleNo(validatedCommand.vehicleNo());
        if (vehicle == null) {
            throw new BusinessException(ErrorCode.VEHICLE_NOT_FOUND);
        }

        RouteReferenceDataObject route = routeReferenceMapper
                .selectByRouteNo(validatedCommand.routeNo());
        if (route == null) {
            throw new BusinessException(ErrorCode.ROUTE_NOT_FOUND);
        }

        var now = clock.instant();
        BusTrip trip = BusTrip.draft(
                tripIdGenerator.nextId(),
                TripNumber.generate(),
                VehicleId.of(vehicle.getId()),
                RouteId.of(route.getId()),
                validatedCommand.departureTime(),
                validatedCommand.bookingDeadline(),
                validatedCommand.priceMoney(),
                now
        );
        tripRepository.save(trip);
        return TripAdminView.from(trip);
    }

    public TripAdminView findByTripNumber(String tripNumber) {
        if (tripNumber == null || tripNumber.isBlank()) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        BusTrip trip = tripRepository
                .findByTripNumber(TripNumber.of(tripNumber.strip()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_NOT_FOUND
                ));
        return TripAdminView.from(trip);
    }

    public TripAdminView publishTrip(PublishTripCommand command) {
        return publishTransaction.publish(command);
    }
}
