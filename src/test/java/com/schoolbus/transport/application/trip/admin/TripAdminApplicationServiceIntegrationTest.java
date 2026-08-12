package com.schoolbus.transport.application.trip.admin;

import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.TripNumber;
import com.schoolbus.transport.domain.trip.TripStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration-test")
class TripAdminApplicationServiceIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-12T00:00:00Z");
    private static final Instant BOOKING_DEADLINE =
            Instant.parse("2026-08-20T08:30:00Z");
    private static final Instant DEPARTURE_TIME =
            Instant.parse("2026-08-20T09:00:00Z");
    private static final String VEHICLE_NO =
            "33333333-3333-3333-3333-333333333333";
    private static final String ROUTE_NO =
            "22222222-2222-2222-2222-222222222222";

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(
                    DockerImageName.parse("mysql:8.4.0")
            )
                    .withDatabaseName("school_bus_admin_test")
                    .withUsername("school_bus")
                    .withPassword("school_bus");

    @Autowired
    private TripAdminApplicationService adminService;

    @Autowired
    private BusTripRepository tripRepository;

    @Autowired
    private SeatInventoryRepository seatInventoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedVehicleAndRoute() {
        jdbcTemplate.update("DELETE FROM booking_trip_inventory");
        jdbcTemplate.update("DELETE FROM transport_trip_seat");
        jdbcTemplate.update("DELETE FROM transport_trip");
        jdbcTemplate.update("DELETE FROM transport_vehicle_seat");
        jdbcTemplate.update("DELETE FROM transport_route");
        jdbcTemplate.update("DELETE FROM transport_vehicle");

        Timestamp now = Timestamp.from(CREATED_AT);
        jdbcTemplate.update(
                """
                INSERT INTO transport_vehicle (
                    id, vehicle_no, license_plate, seat_count,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                3001L,
                VEHICLE_NO,
                "ADMIN-001",
                3,
                "ENABLED",
                0L,
                now,
                now
        );
        jdbcTemplate.update(
                """
                INSERT INTO transport_vehicle_seat (
                    vehicle_id, seat_number, created_at
                ) VALUES (?, ?, ?), (?, ?, ?), (?, ?, ?)
                """,
                3001L, "A01", now,
                3001L, "A02", now,
                3001L, "A03", now
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
                ROUTE_NO,
                "ADMIN-ROUTE",
                "MAIN",
                "EAST",
                45,
                "ENABLED",
                0L,
                now,
                now
        );
    }

    @Test
    void shouldCreatePublishTripAndInitializeSeatsAndInventory() {
        TripAdminView draft = adminService.createDraftTrip(
                new CreateDraftTripCommand(
                        VEHICLE_NO,
                        ROUTE_NO,
                        DEPARTURE_TIME,
                        BOOKING_DEADLINE,
                        new BigDecimal("5.50")
                )
        );

        assertThat(draft.status()).isEqualTo(TripStatus.DRAFT);
        assertThat(draft.version()).isZero();

        TripAdminView published = adminService.publishTrip(
                new PublishTripCommand(
                        draft.tripNumber(),
                        draft.version()
                )
        );

        assertThat(published.status())
                .isEqualTo(TripStatus.OPEN_FOR_BOOKING);
        assertThat(published.version()).isEqualTo(1L);

        BusTrip persisted = tripRepository
                .findByTripNumber(TripNumber.of(draft.tripNumber()))
                .orElseThrow();
        assertThat(persisted.status())
                .isEqualTo(TripStatus.OPEN_FOR_BOOKING);

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM transport_trip_seat
                WHERE trip_id = ? AND status = 'AVAILABLE'
                """,
                Integer.class,
                persisted.tripId().value()
        )).isEqualTo(3);

        assertThat(seatInventoryRepository
                .findByTripReference(
                        TripReference.of(persisted.tripId().value())
                )
                .orElseThrow()
                .availableSeats()).isEqualTo(3);
    }
}
