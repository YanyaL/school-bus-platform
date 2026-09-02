package com.schoolbus.transport.application.vehicle;

import com.schoolbus.transport.domain.vehicle.LicensePlate;
import com.schoolbus.transport.domain.vehicle.SeatLayout;
import com.schoolbus.transport.domain.vehicle.Vehicle;
import com.schoolbus.transport.domain.vehicle.VehicleNumber;
import com.schoolbus.transport.domain.vehicle.VehicleRepository;
import com.schoolbus.transport.config.ConditionalOnEmbeddedTransportAdmin;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

@Service
@ConditionalOnEmbeddedTransportAdmin
@Profile("!test")
public class VehicleCreationTransaction {

    private final VehicleRepository vehicleRepository;
    private final Clock clock;

    public VehicleCreationTransaction(
            VehicleRepository vehicleRepository,
            Clock clock
    ) {
        this.vehicleRepository = Objects.requireNonNull(
                vehicleRepository,
                "vehicleRepository must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public Vehicle create(LicensePlate licensePlate, int seatCount) {
        LicensePlate validatedPlate = Objects.requireNonNull(
                licensePlate,
                "licensePlate must not be null"
        );
        if (vehicleRepository.findByLicensePlate(validatedPlate).isPresent()) {
            throw new DuplicateLicensePlateException(
                    validatedPlate.value()
            );
        }

        VehicleNumber vehicleNumber = VehicleNumber.generate();
        if (vehicleRepository.findByVehicleNumber(vehicleNumber).isPresent()) {
            throw new DuplicateVehicleNumberException(
                    vehicleNumber.value()
            );
        }

        Vehicle vehicle = Vehicle.create(
                vehicleNumber,
                validatedPlate,
                seatCount,
                clock.instant()
        );
        Vehicle savedVehicle = vehicleRepository.save(vehicle);
        SeatLayout seatLayout = savedVehicle.seatLayout();
        vehicleRepository.saveSeatTemplate(
                savedVehicle.id(),
                seatLayout,
                savedVehicle.createdAt()
        );
        return savedVehicle;
    }
}
