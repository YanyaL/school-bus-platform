package com.schoolbus.transport.application.vehicle;

import com.schoolbus.transport.domain.vehicle.LicensePlate;
import com.schoolbus.transport.domain.vehicle.SeatLayout;
import com.schoolbus.transport.domain.vehicle.Vehicle;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import com.schoolbus.transport.domain.vehicle.VehicleNumber;
import com.schoolbus.transport.domain.vehicle.VehicleRepository;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class VehicleManagementApplicationService {

    private final VehicleRepository vehicleRepository;
    private final VehicleCreationTransaction creationTransaction;
    private final Clock clock;

    public VehicleManagementApplicationService(
            VehicleRepository vehicleRepository,
            VehicleCreationTransaction creationTransaction,
            Clock clock
    ) {
        this.vehicleRepository = Objects.requireNonNull(
                vehicleRepository,
                "vehicleRepository must not be null"
        );
        this.creationTransaction = Objects.requireNonNull(
                creationTransaction,
                "creationTransaction must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public VehicleView createVehicle(CreateVehicleCommand command) {
        CreateVehicleCommand validated = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        try {
            Vehicle vehicle = creationTransaction.create(
                    LicensePlate.of(validated.licensePlate()),
                    validated.seatCount()
            );
            return toView(vehicle);
        } catch (DataIntegrityViolationException exception) {
            throw mapIntegrityViolation(exception);
        }
    }

    public VehicleView findById(long vehicleId) {
        Vehicle vehicle = vehicleRepository
                .findById(VehicleId.of(vehicleId))
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId));
        return toView(vehicle);
    }

    public List<VehicleView> listVehicles(
            VehicleStatus status,
            int page,
            int size
    ) {
        int offset = page * size;
        return vehicleRepository
                .findAll(status, offset, size)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public VehicleView updateStatus(UpdateVehicleStatusCommand command) {
        UpdateVehicleStatusCommand validated = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        Vehicle vehicle = vehicleRepository
                .findById(VehicleId.of(validated.vehicleId()))
                .orElseThrow(() -> new VehicleNotFoundException(
                        validated.vehicleId()
                ));
        if (vehicle.version() != validated.expectedVersion()) {
            throw new com.schoolbus.shared.api.BusinessException(
                    com.schoolbus.shared.api.ErrorCode.VERSION_CONFLICT
            );
        }

        VehicleStatus targetStatus = parseStatus(validated.status());
        try {
            applyStatusChange(vehicle, targetStatus);
        } catch (IllegalStateException exception) {
            throw new VehicleStatusConflictException(
                    exception.getMessage()
            );
        }

        try {
            return toView(vehicleRepository.save(vehicle));
        } catch (OptimisticLockingFailureException exception) {
            throw new com.schoolbus.shared.api.BusinessException(
                    com.schoolbus.shared.api.ErrorCode.VERSION_CONFLICT
            );
        }
    }

    private void applyStatusChange(
            Vehicle vehicle,
            VehicleStatus targetStatus
    ) {
        if (targetStatus == VehicleStatus.ENABLED) {
            vehicle.enable(clock.instant());
            return;
        }
        if (targetStatus == VehicleStatus.DISABLED) {
            vehicle.disable(clock.instant());
            return;
        }
        throw new IllegalArgumentException(
                "unsupported vehicle status: " + targetStatus
        );
    }

    private VehicleStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException(
                    "status must not be blank"
            );
        }
        return VehicleStatus.valueOf(status.strip().toUpperCase());
    }

    private RuntimeException mapIntegrityViolation(
            RuntimeException exception
    ) {
        String message = exception.getMessage();
        if (message != null && message.contains("uk_transport_vehicle_license_plate")) {
            return new DuplicateLicensePlateException("duplicate");
        }
        if (message != null && message.contains("uk_transport_vehicle_no")) {
            return new DuplicateVehicleNumberException("duplicate");
        }
        return exception;
    }

    private VehicleView toView(Vehicle vehicle) {
        return new VehicleView(
                vehicle.id().value(),
                vehicle.vehicleNumber().value(),
                vehicle.licensePlate().value(),
                vehicle.seatCount(),
                vehicle.status(),
                vehicle.version(),
                vehicle.createdAt(),
                vehicle.updatedAt()
        );
    }
}
