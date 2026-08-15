package com.schoolbus.transport.infrastructure.persistence.trip;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.route.RouteId;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
@Profile("!test")
public class MyBatisBusTripRepository
        implements BusTripRepository {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final TripMapper tripMapper;

    public MyBatisBusTripRepository(TripMapper tripMapper) {
        this.tripMapper = Objects.requireNonNull(
                tripMapper,
                "tripMapper must not be null"
        );
    }

    @Override
    public BusTrip save(BusTrip trip) {
        BusTrip validatedTrip = Objects.requireNonNull(
                trip,
                "trip must not be null"
        );
        TripDataObject dataObject = toDataObject(validatedTrip);

        if (validatedTrip.version() == 0L) {
            int insertedRows = tripMapper.insertTrip(dataObject);
            if (insertedRows != 1) {
                throw new IllegalStateException(
                        "failed to insert bus trip"
                );
            }
            return validatedTrip;
        }

        long expectedVersion = validatedTrip.version() - 1L;
        int updatedRows = tripMapper.updateWithVersion(
                dataObject,
                expectedVersion
        );
        if (updatedRows != 1) {
            throw new OptimisticLockingFailureException(
                    "bus trip was modified by another request"
            );
        }
        return validatedTrip;
    }

    @Override
    public Optional<BusTrip> findById(TripId tripId) {
        TripId validatedTripId = Objects.requireNonNull(
                tripId,
                "tripId must not be null"
        );
        TripDataObject dataObject = tripMapper.selectById(
                validatedTripId.value()
        );
        if (dataObject == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(dataObject));
    }

    @Override
    public Optional<BusTrip> findByIdForShare(TripId tripId) {
        return findByIdUsing(
                tripId,
                tripMapper::selectByIdForShare
        );
    }

    @Override
    public Optional<BusTrip> findByIdForUpdate(TripId tripId) {
        return findByIdUsing(
                tripId,
                tripMapper::selectByIdForUpdate
        );
    }

    @Override
    public Optional<BusTrip> findByTripNumber(TripNumber tripNumber) {
        TripNumber validated = Objects.requireNonNull(
                tripNumber,
                "tripNumber must not be null"
        );
        TripDataObject dataObject = tripMapper.selectByTripNumber(
                validated.toString()
        );
        if (dataObject == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(dataObject));
    }

    @Override
    public Optional<BusTrip> findByTripNumberForShare(
            TripNumber tripNumber
    ) {
        TripNumber validated = Objects.requireNonNull(
                tripNumber,
                "tripNumber must not be null"
        );
        TripDataObject dataObject = tripMapper.selectByTripNumberForShare(
                validated.toString()
        );
        if (dataObject == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(dataObject));
    }

    @Override
    public List<BusTrip> findAll(
            TripStatus status,
            int offset,
            int limit
    ) {
        if (offset < 0) {
            throw new IllegalArgumentException(
                    "offset must not be negative"
            );
        }
        return tripMapper.selectAll(
                        status == null ? null : status.name(),
                        offset,
                        requirePositiveLimit(limit)
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsVehicleScheduleConflict(
            VehicleId vehicleId,
            Instant departureTime,
            Instant arrivalTime
    ) {
        VehicleId validatedVehicleId = Objects.requireNonNull(
                vehicleId,
                "vehicleId must not be null"
        );
        Instant validatedDepartureTime = Objects.requireNonNull(
                departureTime,
                "departureTime must not be null"
        );
        Instant validatedArrivalTime = Objects.requireNonNull(
                arrivalTime,
                "arrivalTime must not be null"
        );
        if (!validatedDepartureTime.isBefore(validatedArrivalTime)) {
            throw new IllegalArgumentException(
                    "departureTime must be before arrivalTime"
            );
        }
        return tripMapper.existsVehicleScheduleConflict(
                validatedVehicleId.value(),
                toDatabaseTime(validatedDepartureTime),
                toDatabaseTime(validatedArrivalTime)
        );
    }

    @Override
    public List<BusTrip> findBookableTrips(
            Instant now,
            int limit
    ) {
        return tripMapper.selectBookableTrips(
                        toDatabaseTime(now),
                        requirePositiveLimit(limit)
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BusTrip> findDueOpenTripsForClosing(
            Instant now,
            int limit
    ) {
        return tripMapper.selectDueOpenTripsForClosing(
                        toDatabaseTime(now),
                        requirePositiveLimit(limit)
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BusTrip> findDueClosedTripsForDeparture(
            Instant now,
            int limit
    ) {
        return tripMapper.selectDueClosedTripsForDeparture(
                        toDatabaseTime(now),
                        requirePositiveLimit(limit)
                )
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private TripDataObject toDataObject(BusTrip trip) {
        TripDataObject dataObject = new TripDataObject();
        dataObject.setId(trip.tripId().value());
        dataObject.setTripNumber(trip.tripNumber().toString());
        dataObject.setVehicleId(trip.vehicleId().value());
        dataObject.setRouteId(trip.routeId().value());
        dataObject.setDepartureTime(
                LocalDateTime.ofInstant(
                        trip.departureTime(),
                        DATABASE_ZONE
                )
        );
        dataObject.setBookingDeadline(
                LocalDateTime.ofInstant(
                        trip.bookingDeadline(),
                        DATABASE_ZONE
                )
        );
        dataObject.setPrice(trip.price().amount());
        dataObject.setStatus(trip.status().name());
        dataObject.setVersion(trip.version());
        dataObject.setCreatedAt(
                LocalDateTime.ofInstant(
                        trip.createdAt(),
                        DATABASE_ZONE
                )
        );
        dataObject.setUpdatedAt(
                LocalDateTime.ofInstant(
                        trip.updatedAt(),
                        DATABASE_ZONE
                )
        );
        return dataObject;
    }

    private BusTrip toDomain(TripDataObject dataObject) {
        return BusTrip.restore(
                TripId.of(dataObject.getId()),
                TripNumber.of(dataObject.getTripNumber()),
                VehicleId.of(dataObject.getVehicleId()),
                RouteId.of(dataObject.getRouteId()),
                dataObject.getDepartureTime()
                        .toInstant(DATABASE_ZONE),
                dataObject.getBookingDeadline()
                        .toInstant(DATABASE_ZONE),
                new Money(dataObject.getPrice()),
                TripStatus.valueOf(dataObject.getStatus()),
                dataObject.getVersion(),
                dataObject.getCreatedAt()
                        .toInstant(DATABASE_ZONE),
                dataObject.getUpdatedAt()
                        .toInstant(DATABASE_ZONE)
        );
    }

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(
                Objects.requireNonNull(
                        instant,
                        "now must not be null"
                ),
                DATABASE_ZONE
        );
    }

    private Optional<BusTrip> findByIdUsing(
            TripId tripId,
            java.util.function.Function<Long, TripDataObject> query
    ) {
        TripId validatedTripId = Objects.requireNonNull(
                tripId,
                "tripId must not be null"
        );
        TripDataObject dataObject = Objects.requireNonNull(
                query,
                "query must not be null"
        ).apply(validatedTripId.value());
        return dataObject == null
                ? Optional.empty()
                : Optional.of(toDomain(dataObject));
    }

    private int requirePositiveLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "limit must be positive"
            );
        }
        return limit;
    }
}
