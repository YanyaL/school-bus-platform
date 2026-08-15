package com.schoolbus.transportquery.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@MybatisTest
class TripReadMapperIntegrationTest {

    private static final LocalDateTime NOW =
            LocalDateTime.ofInstant(Instant.parse("2026-08-04T08:00:00Z"), ZoneOffset.UTC);

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4.0"))
                    .withDatabaseName("school_bus_query_test")
                    .withUsername("school_bus")
                    .withPassword("school_bus");

    @DynamicPropertySource
    static void disableFlyway(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private TripReadMapper tripReadMapper;

    @Autowired
    private TripSeatReadMapper tripSeatReadMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createSchemaAndSeed() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS transport_trip (
                    id BIGINT PRIMARY KEY,
                    trip_no CHAR(36) NOT NULL,
                    vehicle_id BIGINT NOT NULL,
                    route_id BIGINT NOT NULL,
                    departure_time DATETIME(3) NOT NULL,
                    booking_deadline DATETIME(3) NOT NULL,
                    price DECIMAL(10,2) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    version BIGINT NOT NULL,
                    created_at DATETIME(3) NOT NULL,
                    updated_at DATETIME(3) NOT NULL
                )
                """
        );
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS transport_trip_seat (
                    trip_id BIGINT NOT NULL,
                    seat_number VARCHAR(16) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    version BIGINT NOT NULL,
                    created_at DATETIME(3) NOT NULL,
                    updated_at DATETIME(3) NOT NULL,
                    PRIMARY KEY (trip_id, seat_number)
                )
                """
        );
        jdbcTemplate.update("DELETE FROM transport_trip_seat");
        jdbcTemplate.update("DELETE FROM transport_trip");

        Timestamp createdAt = Timestamp.from(Instant.parse("2026-08-03T00:00:00Z"));
        jdbcTemplate.update(
                """
                INSERT INTO transport_trip (
                    id, trip_no, vehicle_id, route_id, departure_time, booking_deadline,
                    price, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                1101L,
                "11111111-1111-1111-1111-111111111111",
                3101L,
                2101L,
                Timestamp.valueOf(NOW.plusHours(2)),
                Timestamp.valueOf(NOW.plusHours(1)),
                new BigDecimal("5.00"),
                "OPEN_FOR_BOOKING",
                0L,
                createdAt,
                createdAt
        );
        jdbcTemplate.update(
                """
                INSERT INTO transport_trip_seat (
                    trip_id, seat_number, status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
                """,
                1101L, "B01", "LOCKED", 0L, createdAt, createdAt,
                1101L, "A01", "AVAILABLE", 0L, createdAt, createdAt
        );
    }

    @Test
    void shouldSelectBookableTrips() {
        List<TripReadDataObject> trips = tripReadMapper.selectBookableTrips(NOW, 20);
        assertThat(trips).hasSize(1);
        assertThat(trips.getFirst().getTripNumber())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }

    @Test
    void shouldSelectByTripNumber() {
        TripReadDataObject trip = tripReadMapper.selectByTripNumber(
                "11111111-1111-1111-1111-111111111111"
        );
        assertThat(trip.getId()).isEqualTo(1101L);
    }

    @Test
    void shouldSelectSeatsOrderedBySeatNumber() {
        List<TripSeatStatusDataObject> seats =
                tripSeatReadMapper.selectSeatStatusesByTripId(1101L);
        assertThat(seats).extracting(TripSeatStatusDataObject::getSeatNumber)
                .containsExactly("A01", "B01");
    }
}
