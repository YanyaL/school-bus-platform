package com.schoolbus.transport.application.vehicle;

import com.schoolbus.transport.domain.vehicle.LicensePlate;
import com.schoolbus.transport.domain.vehicle.SeatLayout;
import com.schoolbus.transport.domain.vehicle.Vehicle;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import com.schoolbus.transport.domain.vehicle.VehicleNumber;
import com.schoolbus.transport.domain.vehicle.VehicleRepository;
import com.schoolbus.transport.domain.vehicle.VehicleStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleCreationTransactionTest {

    private static final Instant NOW =
            Instant.parse("2026-08-12T00:00:00Z");

    @Mock
    private VehicleRepository vehicleRepository;

    private VehicleCreationTransaction transaction;

    @BeforeEach
    void setUp() {
        transaction = new VehicleCreationTransaction(
                vehicleRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreateVehicleAndSeatTemplateInSameFlow() {
        when(vehicleRepository.findByLicensePlate(any()))
                .thenReturn(Optional.empty());
        when(vehicleRepository.findByVehicleNumber(any()))
                .thenReturn(Optional.empty());
        when(vehicleRepository.save(any())).thenAnswer(invocation -> {
            Vehicle vehicle = invocation.getArgument(0);
            return vehicle.withId(VehicleId.of(4001L));
        });

        Vehicle vehicle = transaction.create(
                LicensePlate.of("粤A12345"),
                3
        );

        assertThat(vehicle.id()).isEqualTo(VehicleId.of(4001L));
        ArgumentCaptor<SeatLayout> layoutCaptor =
                ArgumentCaptor.forClass(SeatLayout.class);
        verify(vehicleRepository).save(any(Vehicle.class));
        verify(vehicleRepository).saveSeatTemplate(
                eq(VehicleId.of(4001L)),
                layoutCaptor.capture(),
                eq(NOW)
        );
        assertThat(layoutCaptor.getValue().seatNumbers())
                .containsExactly("1", "2", "3");
    }

    @Test
    void shouldRejectDuplicateLicensePlate() {
        when(vehicleRepository.findByLicensePlate(LicensePlate.of("粤A12345")))
                .thenReturn(Optional.of(sampleVehicle()));

        assertThatThrownBy(() -> transaction.create(
                LicensePlate.of("粤A12345"),
                50
        )).isInstanceOf(DuplicateLicensePlateException.class);

        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void shouldRollbackWhenSeatTemplateInitializationFails() {
        when(vehicleRepository.findByLicensePlate(any()))
                .thenReturn(Optional.empty());
        when(vehicleRepository.findByVehicleNumber(any()))
                .thenReturn(Optional.empty());
        when(vehicleRepository.save(any())).thenAnswer(invocation -> {
            Vehicle vehicle = invocation.getArgument(0);
            return vehicle.withId(VehicleId.of(4002L));
        });
        org.mockito.Mockito
                .doThrow(new IllegalStateException("seat insert failed"))
                .when(vehicleRepository)
                .saveSeatTemplate(any(), any(), any());

        assertThatThrownBy(() -> transaction.create(
                LicensePlate.of("粤A99999"),
                2
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("seat insert failed");

        var inOrder = inOrder(vehicleRepository);
        inOrder.verify(vehicleRepository).save(any(Vehicle.class));
        inOrder.verify(vehicleRepository).saveSeatTemplate(
                eq(VehicleId.of(4002L)),
                any(SeatLayout.class),
                eq(NOW)
        );
    }

    private Vehicle sampleVehicle() {
        return Vehicle.restore(
                VehicleId.of(3001L),
                VehicleNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                LicensePlate.of("粤A12345"),
                50,
                VehicleStatus.ENABLED,
                0L,
                NOW,
                NOW
        );
    }
}
