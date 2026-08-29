package com.schoolbus.transport.domain.vehicle;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void shouldCreateEnabledVehicle() {
        Vehicle vehicle = Vehicle.create(
                VehicleNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                LicensePlate.of("粤A12345"),
                50,
                CREATED_AT
        );

        assertThat(vehicle.isNew()).isTrue();
        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ENABLED);
        assertThat(vehicle.seatCount()).isEqualTo(50);
        assertThat(vehicle.seatLayout().seatNumbers())
                .hasSize(50)
                .first()
                .isEqualTo("1");
        assertThat(vehicle.seatLayout().seatNumbers().getLast())
                .isEqualTo("50");
    }

    @Test
    void shouldRejectBlankVehicleNumber() {
        assertThatThrownBy(() -> VehicleNumber.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vehicleNumber must not be blank");
    }

    @Test
    void shouldRejectBlankLicensePlate() {
        assertThatThrownBy(() -> LicensePlate.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("licensePlate must not be blank");
    }

    @Test
    void shouldRejectZeroSeatCount() {
        assertThatThrownBy(() -> Vehicle.create(
                VehicleNumber.generate(),
                LicensePlate.of("粤A12345"),
                0,
                CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectSeatCountAboveMaximum() {
        assertThatThrownBy(() -> Vehicle.create(
                VehicleNumber.generate(),
                LicensePlate.of("粤A12345"),
                121,
                CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldEnableAndDisableVehicle() {
        Vehicle vehicle = restoredVehicle(VehicleStatus.DISABLED);

        vehicle.enable(CREATED_AT.plusSeconds(10));

        assertThat(vehicle.status()).isEqualTo(VehicleStatus.ENABLED);
        assertThat(vehicle.version()).isEqualTo(1L);

        vehicle.disable(CREATED_AT.plusSeconds(20));

        assertThat(vehicle.status()).isEqualTo(VehicleStatus.DISABLED);
        assertThat(vehicle.version()).isEqualTo(2L);
    }

    @Test
    void shouldRejectEnablingAlreadyEnabledVehicle() {
        Vehicle vehicle = restoredVehicle(VehicleStatus.ENABLED);

        assertThatThrownBy(() -> vehicle.enable(CREATED_AT.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("vehicle is already enabled");
    }

    @Test
    void shouldRejectDisablingAlreadyDisabledVehicle() {
        Vehicle vehicle = restoredVehicle(VehicleStatus.DISABLED);

        assertThatThrownBy(() -> vehicle.disable(CREATED_AT.plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("vehicle is already disabled");
    }

    private Vehicle restoredVehicle(VehicleStatus status) {
        return Vehicle.restore(
                VehicleId.of(3001L),
                VehicleNumber.of(
                        "22222222-2222-2222-2222-222222222222"
                ),
                LicensePlate.of("粤A12345"),
                50,
                status,
                0L,
                CREATED_AT,
                CREATED_AT
        );
    }
}
