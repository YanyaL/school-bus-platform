package com.schoolbus.transport.infrastructure.persistence.vehicle;

import com.schoolbus.transport.domain.vehicle.LicensePlate;
import com.schoolbus.transport.domain.vehicle.SeatLayout;
import com.schoolbus.transport.domain.vehicle.Vehicle;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import com.schoolbus.transport.domain.vehicle.VehicleNumber;
import com.schoolbus.transport.domain.vehicle.VehicleRepository;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;
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
public class MyBatisVehicleRepository implements VehicleRepository {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;

    private final VehicleMapper vehicleMapper;
    private final VehicleSeatMapper vehicleSeatMapper;

    public MyBatisVehicleRepository(
            VehicleMapper vehicleMapper,
            VehicleSeatMapper vehicleSeatMapper
    ) {
        this.vehicleMapper = Objects.requireNonNull(
                vehicleMapper,
                "vehicleMapper must not be null"
        );
        this.vehicleSeatMapper = Objects.requireNonNull(
                vehicleSeatMapper,
                "vehicleSeatMapper must not be null"
        );
    }

    @Override
    public Vehicle save(Vehicle vehicle) {
        Vehicle validatedVehicle = Objects.requireNonNull(
                vehicle,
                "vehicle must not be null"
        );
        if (validatedVehicle.isNew()) {
            VehicleDataObject dataObject = toDataObject(validatedVehicle);
            int insertedRows = vehicleMapper.insertVehicle(dataObject);
            if (insertedRows != 1 || dataObject.getId() == null) {
                throw new IllegalStateException(
                        "failed to insert vehicle"
                );
            }
            return validatedVehicle.withId(
                    VehicleId.of(dataObject.getId())
            );
        }

        VehicleDataObject dataObject = toDataObject(validatedVehicle);
        long expectedVersion = validatedVehicle.version() - 1L;
        int updatedRows = vehicleMapper.updateWithVersion(
                dataObject,
                expectedVersion
        );
        if (updatedRows != 1) {
            throw new OptimisticLockingFailureException(
                    "vehicle was modified by another request"
            );
        }
        return validatedVehicle;
    }

    @Override
    public Optional<Vehicle> findById(VehicleId vehicleId) {
        VehicleId validatedId = Objects.requireNonNull(
                vehicleId,
                "vehicleId must not be null"
        );
        VehicleDataObject dataObject = vehicleMapper.selectById(
                validatedId.value()
        );
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public Optional<Vehicle> findByIdForUpdate(VehicleId vehicleId) {
        VehicleId validatedId = Objects.requireNonNull(
                vehicleId,
                "vehicleId must not be null"
        );
        VehicleDataObject dataObject = vehicleMapper.selectByIdForUpdate(
                validatedId.value()
        );
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public Optional<Vehicle> findByVehicleNumber(VehicleNumber vehicleNumber) {
        VehicleNumber validatedNumber = Objects.requireNonNull(
                vehicleNumber,
                "vehicleNumber must not be null"
        );
        VehicleDataObject dataObject = vehicleMapper.selectByVehicleNumber(
                validatedNumber.value()
        );
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public Optional<Vehicle> findByLicensePlate(LicensePlate licensePlate) {
        LicensePlate validatedPlate = Objects.requireNonNull(
                licensePlate,
                "licensePlate must not be null"
        );
        VehicleDataObject dataObject = vehicleMapper.selectByLicensePlate(
                validatedPlate.value()
        );
        return Optional.ofNullable(dataObject).map(this::toDomain);
    }

    @Override
    public List<Vehicle> findAll(
            VehicleStatus status,
            int offset,
            int limit
    ) {
        String statusFilter = status == null ? null : status.name();
        return vehicleMapper
                .selectAll(statusFilter, offset, limit)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int count(VehicleStatus status) {
        String statusFilter = status == null ? null : status.name();
        return vehicleMapper.count(statusFilter);
    }

    @Override
    public void saveSeatTemplate(
            VehicleId vehicleId,
            SeatLayout seatLayout,
            Instant createdAt
    ) {
        VehicleId validatedId = Objects.requireNonNull(
                vehicleId,
                "vehicleId must not be null"
        );
        SeatLayout validatedLayout = Objects.requireNonNull(
                seatLayout,
                "seatLayout must not be null"
        );
        Instant operationTime = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
        LocalDateTime createdAtLocal = LocalDateTime.ofInstant(
                operationTime,
                DATABASE_ZONE
        );
        int insertedRows = vehicleSeatMapper.insertSeats(
                validatedId.value(),
                validatedLayout.seatNumbers(),
                createdAtLocal
        );
        if (insertedRows != validatedLayout.seatCount()) {
            throw new IllegalStateException(
                    "failed to insert all vehicle seat templates"
            );
        }
    }

    @Override
    public List<String> findSeatNumbersByVehicleId(VehicleId vehicleId) {
        VehicleId validatedId = Objects.requireNonNull(
                vehicleId,
                "vehicleId must not be null"
        );
        return vehicleSeatMapper.selectSeatNumbersByVehicleId(
                validatedId.value()
        );
    }

    private VehicleDataObject toDataObject(Vehicle vehicle) {
        VehicleDataObject dataObject = new VehicleDataObject();
        if (!vehicle.isNew()) {
            dataObject.setId(vehicle.id().value());
        }
        dataObject.setVehicleNumber(vehicle.vehicleNumber().value());
        dataObject.setLicensePlate(vehicle.licensePlate().value());
        dataObject.setSeatCount(vehicle.seatCount());
        dataObject.setStatus(vehicle.status().name());
        dataObject.setVersion(vehicle.version());
        dataObject.setCreatedAt(toLocalDateTime(vehicle.createdAt()));
        dataObject.setUpdatedAt(toLocalDateTime(vehicle.updatedAt()));
        return dataObject;
    }

    private Vehicle toDomain(VehicleDataObject dataObject) {
        return Vehicle.restore(
                VehicleId.of(dataObject.getId()),
                VehicleNumber.of(dataObject.getVehicleNumber()),
                LicensePlate.of(dataObject.getLicensePlate()),
                dataObject.getSeatCount(),
                VehicleStatus.valueOf(dataObject.getStatus()),
                dataObject.getVersion(),
                toInstant(dataObject.getCreatedAt()),
                toInstant(dataObject.getUpdatedAt())
        );
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, DATABASE_ZONE);
    }

    private static Instant toInstant(LocalDateTime localDateTime) {
        return localDateTime.toInstant(DATABASE_ZONE);
    }
}
