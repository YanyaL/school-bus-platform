package com.schoolbus.transport.infrastructure.persistence.vehicle;

import com.schoolbus.transport.domain.vehicle.LicensePlate;
import com.schoolbus.transport.domain.vehicle.SeatLayout;
import com.schoolbus.transport.domain.vehicle.Vehicle;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import com.schoolbus.transport.domain.vehicle.VehicleNumber;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisVehicleRepositoryTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-12T00:00:00Z");
    private static final LocalDateTime CREATED_AT_LOCAL =
            LocalDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC);

    @Mock
    private VehicleMapper vehicleMapper;

    @Mock
    private VehicleSeatMapper vehicleSeatMapper;

    private MyBatisVehicleRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisVehicleRepository(
                vehicleMapper,
                vehicleSeatMapper
        );
    }

    @Test
    void shouldInsertVehicleAndReturnAssignedId() {
        Vehicle vehicle = Vehicle.create(
                VehicleNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                LicensePlate.of("粤A12345"),
                50,
                CREATED_AT
        );
        when(vehicleMapper.insertVehicle(any())).thenAnswer(invocation -> {
            VehicleDataObject dataObject = invocation.getArgument(0);
            dataObject.setId(3001L);
            return 1;
        });

        Vehicle saved = repository.save(vehicle);

        assertThat(saved.id()).isEqualTo(VehicleId.of(3001L));
        ArgumentCaptor<VehicleDataObject> captor =
                ArgumentCaptor.forClass(VehicleDataObject.class);
        verify(vehicleMapper).insertVehicle(captor.capture());
        assertThat(captor.getValue().getLicensePlate())
                .isEqualTo("粤A12345");
        assertThat(captor.getValue().getSeatCount()).isEqualTo(50);
    }

    @Test
    void shouldUpdateVehicleWithOptimisticLock() {
        Vehicle vehicle = Vehicle.restore(
                VehicleId.of(3001L),
                VehicleNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                LicensePlate.of("粤A12345"),
                50,
                VehicleStatus.DISABLED,
                1L,
                CREATED_AT,
                CREATED_AT.plusSeconds(10)
        );
        when(vehicleMapper.updateWithVersion(any(), eq(0L)))
                .thenReturn(1);

        Vehicle saved = repository.save(vehicle);

        assertThat(saved.version()).isEqualTo(1L);
        verify(vehicleMapper).updateWithVersion(
                any(VehicleDataObject.class),
                eq(0L)
        );
    }

    @Test
    void shouldThrowWhenVersionConflictOccurs() {
        Vehicle vehicle = Vehicle.restore(
                VehicleId.of(3001L),
                VehicleNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                LicensePlate.of("粤A12345"),
                50,
                VehicleStatus.DISABLED,
                1L,
                CREATED_AT,
                CREATED_AT.plusSeconds(10)
        );
        when(vehicleMapper.updateWithVersion(any(), eq(0L)))
                .thenReturn(0);

        assertThatThrownBy(() -> repository.save(vehicle))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void shouldFindVehicleById() {
        VehicleDataObject dataObject = dataObject();
        when(vehicleMapper.selectById(3001L)).thenReturn(dataObject);

        Optional<Vehicle> vehicle = repository.findById(
                VehicleId.of(3001L)
        );

        assertThat(vehicle).isPresent();
        assertThat(vehicle.orElseThrow().licensePlate().value())
                .isEqualTo("粤A12345");
    }

    @Test
    void shouldFindVehicleByIdForUpdate() {
        VehicleDataObject dataObject = dataObject();
        when(vehicleMapper.selectByIdForUpdate(3001L))
                .thenReturn(dataObject);

        Optional<Vehicle> vehicle = repository.findByIdForUpdate(
                VehicleId.of(3001L)
        );

        assertThat(vehicle).isPresent();
        assertThat(vehicle.orElseThrow().id())
                .isEqualTo(VehicleId.of(3001L));
    }

    @Test
    void shouldBatchInsertSeatTemplate() {
        when(vehicleSeatMapper.insertSeats(
                eq(3001L),
                eq(List.of("1", "2", "3")),
                eq(CREATED_AT_LOCAL)
        )).thenReturn(3);

        repository.saveSeatTemplate(
                VehicleId.of(3001L),
                SeatLayout.of(3),
                CREATED_AT
        );

        verify(vehicleSeatMapper).insertSeats(
                3001L,
                List.of("1", "2", "3"),
                CREATED_AT_LOCAL
        );
    }

    private VehicleDataObject dataObject() {
        VehicleDataObject dataObject = new VehicleDataObject();
        dataObject.setId(3001L);
        dataObject.setVehicleNumber(
                "11111111-1111-1111-1111-111111111111"
        );
        dataObject.setLicensePlate("粤A12345");
        dataObject.setSeatCount(50);
        dataObject.setStatus(VehicleStatus.ENABLED.name());
        dataObject.setVersion(0L);
        dataObject.setCreatedAt(CREATED_AT_LOCAL);
        dataObject.setUpdatedAt(CREATED_AT_LOCAL);
        return dataObject;
    }
}
