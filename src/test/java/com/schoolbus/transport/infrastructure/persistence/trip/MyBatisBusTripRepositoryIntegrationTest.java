package com.schoolbus.transport.infrastructure.persistence.trip;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.Money;
import com.schoolbus.transport.domain.trip.RouteId;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripStatus;
import com.schoolbus.transport.domain.vehicle.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration-test")
@Transactional
class MyBatisBusTripRepositoryIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-03T00:00:00Z");
    private static final Instant BOOKING_DEADLINE =
            Instant.parse("2026-08-04T07:30:00Z");
    private static final Instant DEPARTURE_TIME =
            Instant.parse("2026-08-04T08:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(
                    DockerImageName.parse("mysql:8.4.0")
            )
                    .withDatabaseName("school_bus_test")
                    .withUsername("school_bus")
                    .withPassword("school_bus");

    @Autowired
    private BusTripRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedRouteAndVehicle() {
        Timestamp now = Timestamp.from(CREATED_AT);
        jdbcTemplate.update(
                """
                INSERT INTO transport_vehicle (
                    id, vehicle_no, license_plate, seat_count,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                3001L,
                "33333333-3333-3333-3333-333333333333",
                "TEST-001",
                45,
                "ENABLED",
                0L,
                now,
                now
        );
        jdbcTemplate.update(
                """
                INSERT INTO transport_route (
                    id, route_no, route_code,
                    departure_campus, arrival_campus,
                    estimated_duration_minutes, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                2001L,
                "22222222-2222-2222-2222-222222222222",
                "MAIN-TO-EAST",
                "MAIN",
                "EAST",
                60,
                "ENABLED",
                0L,
                now,
                now
        );
    }

    @Test
    void shouldSaveAndRestoreTripUsingRealMySql() {
        BusTrip trip = draftTrip();

        repository.save(trip);
        BusTrip restored = repository
                .findById(trip.tripId())
                .orElseThrow();

        assertThat(restored.tripId()).isEqualTo(trip.tripId());
        assertThat(restored.tripNumber())
                .isEqualTo(trip.tripNumber());
        assertThat(restored.vehicleId())
                .isEqualTo(trip.vehicleId());
        assertThat(restored.routeId()).isEqualTo(trip.routeId());
        assertThat(restored.price()).isEqualTo(Money.of("5.00"));
        assertThat(restored.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(restored.version()).isZero();
    }

    @Test
    void shouldRejectStaleConcurrentUpdate() {
        repository.save(draftTrip());
        BusTrip firstRequest = repository
                .findById(TripId.of(1001L))
                .orElseThrow();
        BusTrip staleSecondRequest = repository
                .findById(TripId.of(1001L))
                .orElseThrow();

        firstRequest.openForBooking(CREATED_AT.plusSeconds(60));
        repository.save(firstRequest);

        staleSecondRequest.openForBooking(
                CREATED_AT.plusSeconds(120)
        );
        assertThatThrownBy(
                () -> repository.save(staleSecondRequest)
        ).isInstanceOf(OptimisticLockingFailureException.class);

        BusTrip persisted = repository
                .findById(TripId.of(1001L))
                .orElseThrow();
        assertThat(persisted.status())
                .isEqualTo(TripStatus.OPEN_FOR_BOOKING);
        assertThat(persisted.version()).isEqualTo(1L);
        assertThat(persisted.updatedAt())
                .isEqualTo(CREATED_AT.plusSeconds(60));
    }

    private BusTrip draftTrip() {
        return BusTrip.draft(
                TripId.of(1001L),
                TripNumber.of(
                        "11111111-1111-1111-1111-111111111111"
                ),
                VehicleId.of(3001L),
                RouteId.of(2001L),
                DEPARTURE_TIME,
                BOOKING_DEADLINE,
                Money.of("5.00"),
                CREATED_AT
        );
    }
}
