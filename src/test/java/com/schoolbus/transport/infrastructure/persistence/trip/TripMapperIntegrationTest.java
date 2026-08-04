package com.schoolbus.transport.infrastructure.persistence.trip;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration-test")
@Transactional
class TripMapperIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-03T00:00:00Z");
    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(
                    Instant.parse("2026-08-04T08:00:00Z"),
                    ZoneOffset.UTC
            );

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
    private TripMapper tripMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedRouteAndVehicle() {
        Timestamp createdAt = Timestamp.from(CREATED_AT);
        jdbcTemplate.update(
                """
                INSERT INTO transport_vehicle (
                    id, vehicle_no, license_plate, seat_count,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                3101L,
                "34333333-3333-3333-3333-333333333333",
                "MAPPER-001",
                45,
                "ENABLED",
                0L,
                createdAt,
                createdAt
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
                2101L,
                "23222222-2222-2222-2222-222222222222",
                "MAPPER-ROUTE",
                "MAIN",
                "EAST",
                60,
                "ENABLED",
                0L,
                createdAt,
                createdAt
        );
    }

    @Test
    void shouldSelectOnlyOpenTripsWhoseBookingDeadlineHasArrived() {
        insertTrip(1101L, "OPEN_FOR_BOOKING", -10, 60);
        insertTrip(1102L, "OPEN_FOR_BOOKING", 0, 70);
        insertTrip(1103L, "OPEN_FOR_BOOKING", 10, 80);
        insertTrip(1104L, "CLOSED", -20, 90);

        List<TripDataObject> dueTrips =
                tripMapper.selectDueOpenTripsForClosing(NOW, 100);

        assertThat(dueTrips)
                .extracting(TripDataObject::getId)
                .containsExactly(1101L, 1102L);
    }

    @Test
    void shouldSelectOnlyTripsThatAreStillBookable() {
        insertTrip(1401L, "OPEN_FOR_BOOKING", 30, 60);
        insertTrip(1402L, "OPEN_FOR_BOOKING", 0, 70);
        insertTrip(1403L, "CLOSED", 40, 80);

        List<TripDataObject> bookableTrips =
                tripMapper.selectBookableTrips(NOW, 100);

        assertThat(bookableTrips)
                .extracting(TripDataObject::getId)
                .containsExactly(1401L);
    }

    @Test
    void shouldSelectOnlyClosedTripsWhoseDepartureTimeHasArrived() {
        insertTrip(1201L, "CLOSED", -90, -10);
        insertTrip(1202L, "CLOSED", -80, 0);
        insertTrip(1203L, "CLOSED", -70, 10);
        insertTrip(1204L, "OPEN_FOR_BOOKING", -60, -20);

        List<TripDataObject> dueTrips =
                tripMapper.selectDueClosedTripsForDeparture(NOW, 100);

        assertThat(dueTrips)
                .extracting(TripDataObject::getId)
                .containsExactly(1201L, 1202L);
    }

    @Test
    void shouldRespectBatchLimit() {
        insertTrip(1301L, "OPEN_FOR_BOOKING", -30, 30);
        insertTrip(1302L, "OPEN_FOR_BOOKING", -20, 40);

        List<TripDataObject> dueTrips =
                tripMapper.selectDueOpenTripsForClosing(NOW, 1);

        assertThat(dueTrips)
                .extracting(TripDataObject::getId)
                .containsExactly(1301L);
    }

    private void insertTrip(
            long id,
            String status,
            long bookingDeadlineOffsetMinutes,
            long departureOffsetMinutes
    ) {
        TripDataObject trip = new TripDataObject();
        trip.setId(id);
        trip.setTripNumber(UUID.randomUUID().toString());
        trip.setVehicleId(3101L);
        trip.setRouteId(2101L);
        trip.setBookingDeadline(
                NOW.plusMinutes(bookingDeadlineOffsetMinutes)
        );
        trip.setDepartureTime(
                NOW.plusMinutes(departureOffsetMinutes)
        );
        trip.setPrice(new BigDecimal("5.00"));
        trip.setStatus(status);
        trip.setVersion(0L);
        trip.setCreatedAt(
                LocalDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC)
        );
        trip.setUpdatedAt(
                LocalDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC)
        );
        tripMapper.insertTrip(trip);
    }
}
