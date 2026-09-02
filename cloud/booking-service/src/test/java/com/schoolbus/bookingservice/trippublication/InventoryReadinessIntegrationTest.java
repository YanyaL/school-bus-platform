package com.schoolbus.bookingservice.trippublication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.bookingservice.application.trippublication.InventoryReadinessApplicationService;
import com.schoolbus.bookingservice.config.InventoryReadinessShadowConfiguration;
import com.schoolbus.bookingservice.infrastructure.persistence.trippublication.InventoryReadinessMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class InventoryReadinessIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("inventory_readiness_test")
            .withUsername("readiness")
            .withPassword("readiness");

    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static InventoryReadinessApplicationService service;

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeAll
    static void startContext() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        );
        new ResourceDatabasePopulator(
                new ClassPathResource(
                        "shadow-schema/V8__add_booking_trip_publication_shadow.sql"
                ),
                new ClassPathResource(
                        "shadow-schema/V9__add_booking_inventory_readiness_shadow.sql"
                )
        ).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        createLiveReadModels();

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        var myBatis = new org.apache.ibatis.session.Configuration();
        myBatis.setMapUnderscoreToCamelCase(true);
        myBatis.addMapper(InventoryReadinessMapper.class);
        factory.setConfiguration(myBatis);
        SqlSessionFactory sessions = factory.getObject();

        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource(
                        "readiness-test",
                        Map.of(
                                "school-bus.booking.inventory-readiness-shadow.enabled",
                                "true",
                                "school-bus.booking.inventory-readiness-shadow.batch-size",
                                "10"
                        )
                )
        );
        context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(
                PlatformTransactionManager.class,
                () -> new DataSourceTransactionManager(dataSource)
        );
        context.registerBean(
                InventoryReadinessMapper.class,
                () -> new SqlSessionTemplate(sessions).getMapper(
                        InventoryReadinessMapper.class
                )
        );
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(
                Clock.class,
                () -> Clock.fixed(
                        Instant.parse("2026-09-02T06:00:00Z"),
                        ZoneOffset.UTC
                )
        );
        context.register(Infrastructure.class);
        context.register(InventoryReadinessShadowConfiguration.class);
        context.refresh();
        service = context.getBean(InventoryReadinessApplicationService.class);
    }

    @Test
    void movesFromWaitingToReadyWithoutMutatingLiveInventory() {
        resetRows();
        insertSnapshot(1L, "[\"A01\",\"A02\"]");
        insertInventory(1L, 2);
        insertSeat(1L, "A01");

        assertThat(service.verifyPending().waiting()).isEqualTo(1);
        assertThat(readinessStatus()).isEqualTo("WAITING");
        assertThat(diagnostic()).isEqualTo("SEAT_SET_MISMATCH");

        insertSeat(1L, "A02");
        assertThat(service.verifyPending().ready()).isEqualTo(1);
        assertThat(readinessStatus()).isEqualTo("READY");
        assertThat(jdbc.queryForObject(
                "SELECT available_seats FROM booking_trip_inventory WHERE trip_id=1",
                Integer.class
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM transport_trip_seat WHERE trip_id=1",
                Integer.class
        )).isEqualTo(2);
    }

    @Test
    void sameVersionCannotDowngradeReadyButNewerVersionCanResetIt() {
        resetRows();
        insertSnapshot(1L, "[\"A01\"]");
        insertInventory(1L, 1);
        insertSeat(1L, "A01");
        assertThat(service.verifyPending().ready()).isEqualTo(1);

        jdbc.update("DELETE FROM transport_trip_seat WHERE trip_id=1");
        context.getBean(InventoryReadinessMapper.class).saveObservation(
                1L,
                "11111111-1111-4111-8111-111111111111",
                1L,
                1,
                1,
                0,
                "WAITING",
                "SEAT_SET_MISMATCH",
                LocalDateTime.parse("2026-09-02T06:01:00"),
                null
        );
        assertThat(readinessStatus()).isEqualTo("READY");

        jdbc.update("""
                UPDATE booking_trip_publication_shadow
                SET trip_version=2, updated_at=NOW(3)
                WHERE trip_id=1
                """);
        assertThat(service.verifyPending().waiting()).isEqualTo(1);
        assertThat(readinessStatus()).isEqualTo("WAITING");
        assertThat(jdbc.queryForObject(
                "SELECT publication_version FROM booking_trip_inventory_readiness WHERE trip_id=1",
                Long.class
        )).isEqualTo(2L);
    }

    private static void createLiveReadModels() {
        jdbc.execute("""
                CREATE TABLE booking_trip_inventory (
                    trip_id BIGINT PRIMARY KEY,
                    total_seats INT NOT NULL,
                    available_seats INT NOT NULL
                ) ENGINE=InnoDB
                """);
        jdbc.execute("""
                CREATE TABLE transport_trip_seat (
                    trip_id BIGINT NOT NULL,
                    seat_number VARCHAR(10) NOT NULL,
                    PRIMARY KEY (trip_id, seat_number)
                ) ENGINE=InnoDB
                """);
    }

    private void resetRows() {
        jdbc.update("DELETE FROM booking_trip_inventory_readiness");
        jdbc.update("DELETE FROM booking_trip_publication_shadow");
        jdbc.update("DELETE FROM booking_trip_inventory");
        jdbc.update("DELETE FROM transport_trip_seat");
    }

    private void insertSnapshot(long tripId, String seats) {
        String tripNumber = "11111111-1111-4111-8111-111111111111";
        String snapshot = "{\"seatNumbers\":" + seats + "}";
        jdbc.update("""
                INSERT INTO booking_trip_publication_shadow (
                    trip_id, trip_no, trip_version, payload_hash,
                    snapshot_json, last_event_id, created_at, updated_at
                ) VALUES (?, ?, 1, ?, CAST(? AS JSON), ?, NOW(3), NOW(3))
                """, tripId, tripNumber, "a".repeat(64), snapshot,
                "22222222-2222-4222-8222-222222222222");
    }

    private void insertInventory(long tripId, int totalSeats) {
        jdbc.update("""
                INSERT INTO booking_trip_inventory (
                    trip_id, total_seats, available_seats
                ) VALUES (?, ?, ?)
                """, tripId, totalSeats, totalSeats);
    }

    private void insertSeat(long tripId, String seatNumber) {
        jdbc.update(
                "INSERT INTO transport_trip_seat (trip_id, seat_number) VALUES (?, ?)",
                tripId,
                seatNumber
        );
    }

    private String readinessStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM booking_trip_inventory_readiness WHERE trip_id=1",
                String.class
        );
    }

    private String diagnostic() {
        return jdbc.queryForObject(
                "SELECT diagnostic_code FROM booking_trip_inventory_readiness WHERE trip_id=1",
                String.class
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Infrastructure {
    }
}
