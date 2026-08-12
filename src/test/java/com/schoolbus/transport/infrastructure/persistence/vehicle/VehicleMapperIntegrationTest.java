package com.schoolbus.transport.infrastructure.persistence.vehicle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration-test")
@Transactional
class VehicleMapperIntegrationTest {

    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(
                    java.time.Instant.parse("2026-08-12T00:00:00Z"),
                    ZoneOffset.UTC
            );

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(
                    DockerImageName.parse("mysql:8.4.0")
            )
                    .withDatabaseName("school_bus_vehicle_test")
                    .withUsername("school_bus")
                    .withPassword("school_bus");

    @Autowired
    private VehicleMapper vehicleMapper;

    @Autowired
    private VehicleSeatMapper vehicleSeatMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM transport_vehicle_seat");
        jdbcTemplate.update("DELETE FROM transport_vehicle");
    }

    @Test
    void shouldInsertAndQueryVehicle() {
        VehicleDataObject vehicle = newVehicle(
                "11111111-1111-1111-1111-111111111111",
                "粤A12345"
        );

        assertThat(vehicleMapper.insertVehicle(vehicle)).isEqualTo(1);
        assertThat(vehicle.getId()).isNotNull();

        VehicleDataObject loaded = vehicleMapper.selectById(
                vehicle.getId()
        );

        assertThat(loaded.getLicensePlate()).isEqualTo("粤A12345");
        assertThat(loaded.getSeatCount()).isEqualTo(3);
    }

    @Test
    void shouldRejectDuplicateLicensePlate() {
        vehicleMapper.insertVehicle(newVehicle(
                "11111111-1111-1111-1111-111111111111",
                "粤A12345"
        ));

        assertThatThrownBy(() -> vehicleMapper.insertVehicle(newVehicle(
                "22222222-2222-2222-2222-222222222222",
                "粤A12345"
        ))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldRejectDuplicateVehicleNumber() {
        vehicleMapper.insertVehicle(newVehicle(
                "11111111-1111-1111-1111-111111111111",
                "粤A12345"
        ));

        assertThatThrownBy(() -> vehicleMapper.insertVehicle(newVehicle(
                "11111111-1111-1111-1111-111111111111",
                "粤A99999"
        ))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldUpdateVehicleWithVersionCheck() {
        VehicleDataObject vehicle = newVehicle(
                "11111111-1111-1111-1111-111111111111",
                "粤A12345"
        );
        vehicleMapper.insertVehicle(vehicle);

        vehicle.setStatus("DISABLED");
        vehicle.setVersion(1L);
        vehicle.setUpdatedAt(NOW.plusSeconds(30));

        assertThat(vehicleMapper.updateWithVersion(vehicle, 0L))
                .isEqualTo(1);
        assertThat(vehicleMapper.updateWithVersion(vehicle, 0L))
                .isZero();
    }

    @Test
    void shouldBatchInsertSeatTemplate() {
        VehicleDataObject vehicle = newVehicle(
                "11111111-1111-1111-1111-111111111111",
                "粤A12345"
        );
        vehicleMapper.insertVehicle(vehicle);

        int inserted = vehicleSeatMapper.insertSeats(
                vehicle.getId(),
                List.of("1", "2", "3"),
                NOW
        );

        assertThat(inserted).isEqualTo(3);
        assertThat(vehicleSeatMapper.selectSeatNumbersByVehicleId(
                vehicle.getId()
        )).containsExactly("1", "2", "3");
    }

    private VehicleDataObject newVehicle(
            String vehicleNumber,
            String licensePlate
    ) {
        VehicleDataObject vehicle = new VehicleDataObject();
        vehicle.setVehicleNumber(vehicleNumber);
        vehicle.setLicensePlate(licensePlate);
        vehicle.setSeatCount(3);
        vehicle.setStatus("ENABLED");
        vehicle.setVersion(0L);
        vehicle.setCreatedAt(NOW);
        vehicle.setUpdatedAt(NOW);
        return vehicle;
    }
}
