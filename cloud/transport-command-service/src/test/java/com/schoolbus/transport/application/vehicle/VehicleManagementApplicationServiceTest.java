package com.schoolbus.transport.application.vehicle;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
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
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
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
class VehicleManagementApplicationServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-12T00:00:00Z");

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private VehicleCreationTransaction creationTransaction;

    private VehicleManagementApplicationService service;

    @BeforeEach
    void setUp() {
        service = new VehicleManagementApplicationService(
                vehicleRepository,
                creationTransaction,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldDelegateVehicleCreation() {
        Vehicle vehicle = sampleVehicle(VehicleStatus.ENABLED, 0L);
        when(creationTransaction.create(
                LicensePlate.of("粤A12345"),
                50
        )).thenReturn(vehicle);

        VehicleView view = service.createVehicle(
                new CreateVehicleCommand("粤A12345", 50)
        );

        assertThat(view.vehicleId()).isEqualTo(3001L);
        assertThat(view.seatCount()).isEqualTo(50);
        assertThat(view.status()).isEqualTo(VehicleStatus.ENABLED);
    }

    @Test
    void shouldFindVehicleById() {
        when(vehicleRepository.findById(VehicleId.of(3001L)))
                .thenReturn(Optional.of(sampleVehicle(
                        VehicleStatus.ENABLED,
                        0L
                )));

        VehicleView view = service.findById(3001L);

        assertThat(view.licensePlate()).isEqualTo("粤A12345");
    }

    @Test
    void shouldRejectMissingVehicle() {
        when(vehicleRepository.findById(VehicleId.of(9999L)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(9999L))
                .isInstanceOf(VehicleNotFoundException.class);
    }

    @Test
    void shouldListVehicles() {
        when(vehicleRepository.findAll(VehicleStatus.ENABLED, 0, 20))
                .thenReturn(List.of(sampleVehicle(
                        VehicleStatus.ENABLED,
                        0L
                )));

        List<VehicleView> views = service.listVehicles(
                VehicleStatus.ENABLED,
                0,
                20
        );

        assertThat(views).hasSize(1);
    }

    @Test
    void shouldDisableVehicleWithMatchingVersion() {
        Vehicle vehicle = sampleVehicle(VehicleStatus.ENABLED, 0L);
        when(vehicleRepository.findById(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any())).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        VehicleView view = service.updateStatus(
                new UpdateVehicleStatusCommand(
                        3001L,
                        "DISABLED",
                        0L
                )
        );

        assertThat(view.status()).isEqualTo(VehicleStatus.DISABLED);
        assertThat(view.version()).isEqualTo(1L);
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void shouldRejectStatusUpdateWhenVersionMismatch() {
        when(vehicleRepository.findById(VehicleId.of(3001L)))
                .thenReturn(Optional.of(sampleVehicle(
                        VehicleStatus.ENABLED,
                        2L
                )));

        assertThatThrownBy(() -> service.updateStatus(
                new UpdateVehicleStatusCommand(
                        3001L,
                        "DISABLED",
                        1L
                )
        )).isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
    }

    @Test
    void shouldMapOptimisticLockFailureToVersionConflict() {
        Vehicle vehicle = sampleVehicle(VehicleStatus.ENABLED, 0L);
        when(vehicleRepository.findById(VehicleId.of(3001L)))
                .thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any()))
                .thenThrow(new OptimisticLockingFailureException(
                        "conflict"
                ));

        assertThatThrownBy(() -> service.updateStatus(
                new UpdateVehicleStatusCommand(
                        3001L,
                        "DISABLED",
                        0L
                )
        )).isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).errorCode()
                )
                .isEqualTo(ErrorCode.VERSION_CONFLICT);
    }

    @Test
    void shouldRejectAlreadyDisabledVehicle() {
        when(vehicleRepository.findById(VehicleId.of(3001L)))
                .thenReturn(Optional.of(sampleVehicle(
                        VehicleStatus.DISABLED,
                        1L
                )));

        assertThatThrownBy(() -> service.updateStatus(
                new UpdateVehicleStatusCommand(
                        3001L,
                        "DISABLED",
                        1L
                )
        )).isInstanceOf(VehicleStatusConflictException.class);
    }

    private Vehicle sampleVehicle(
            VehicleStatus status,
            long version
    ) {
        return Vehicle.restore(
                VehicleId.of(3001L),
                VehicleNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                LicensePlate.of("粤A12345"),
                50,
                status,
                version,
                NOW,
                NOW
        );
    }
}
