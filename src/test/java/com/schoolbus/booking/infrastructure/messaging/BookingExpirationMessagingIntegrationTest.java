package com.schoolbus.booking.infrastructure.messaging;

import com.schoolbus.booking.application.booking.BookingApplicationService;
import com.schoolbus.booking.application.booking.CreateBookingCommand;
import com.schoolbus.booking.application.booking.CreateBookingResult;
import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.CancellationReason;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.booking.infrastructure.outbox.BookingExpirationOutboxRelay;
import com.schoolbus.payment.infrastructure.outbox.OutboxRelayResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(
        named = "RUN_MESSAGING_ACCEPTANCE_TESTS",
        matches = "true"
)
@SpringBootTest(properties = {
        "school-bus.booking.payment-window=PT3S",
        "school-bus.messaging.outbox-relay.initial-delay-ms=999999",
        "school-bus.messaging.outbox-relay.fixed-delay-ms=999999"
})
@ActiveProfiles({"integration-test", "local"})
class BookingExpirationMessagingIntegrationTest {

    private static final TripReference TRIP = TripReference.of(9101L);
    private static final String TRIP_NUMBER =
            "91019101-9101-9101-9101-910191019101";
    private static final Instant BASE_TIME = Instant.now();
    private static final int REDIS_PORT = 6379;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(
                    DockerImageName.parse("mysql:8.4.0")
            )
                    .withDatabaseName("school_bus_messaging_test")
                    .withUsername("school_bus")
                    .withPassword("school_bus");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT_MQ =
            new RabbitMQContainer(
                    DockerImageName.parse("rabbitmq:4.1-management")
            );

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            ).withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(REDIS_PORT)
        );
    }

    @Autowired
    private BookingApplicationService bookingApplicationService;

    @Autowired
    private BookingOrderRepository bookingOrderRepository;

    @Autowired
    private SeatInventoryRepository seatInventoryRepository;

    @Autowired
    private BookingExpirationOutboxRelay outboxRelay;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM event_outbox");
        jdbcTemplate.update("DELETE FROM event_consumed");
        jdbcTemplate.update("DELETE FROM payment_record");
        jdbcTemplate.update("DELETE FROM booking_order");
        jdbcTemplate.update("DELETE FROM booking_trip_inventory");
        jdbcTemplate.update("DELETE FROM transport_trip_seat");
        jdbcTemplate.update("DELETE FROM transport_trip");
        jdbcTemplate.update("DELETE FROM transport_route");
        jdbcTemplate.update("DELETE FROM transport_vehicle");
        seedTrip();
    }

    @Test
    void shouldRelayOutboxPublishTtlMessageAndExpireBooking() {
        seedSeat("B01");
        seatInventoryRepository.save(
                SeatInventory.initialize(TRIP, 10, BASE_TIME)
        );

        CreateBookingResult booking = bookingApplicationService.createBooking(
                new CreateBookingCommand(
                        8101L,
                        TRIP_NUMBER,
                        "B01",
                        "messaging-expire-8101"
                )
        );

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM event_outbox
                WHERE context_name = 'booking'
                  AND event_type = 'BookingPaymentDeadlineReached'
                  AND status = 'NEW'
                """,
                Integer.class
        )).isEqualTo(1);

        OutboxRelayResult relayResult = outboxRelay.relayReadyEvents();
        assertThat(relayResult.claimed()).isEqualTo(1);
        assertThat(relayResult.published()).isEqualTo(1);
        assertThat(relayResult.failed()).isZero();

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT status FROM event_outbox
                WHERE context_name = 'booking'
                  AND event_type = 'BookingPaymentDeadlineReached'
                """,
                String.class
        )).isEqualTo("PUBLISHED");

        assertThat(bookingOrderRepository
                .findById(BookingId.of(booking.bookingId()))
                .orElseThrow()
                .status()).isEqualTo(BookingStatus.PENDING_PAYMENT);

        await()
                .atMost(Duration.ofSeconds(12))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> {
                    var order = bookingOrderRepository
                            .findById(BookingId.of(booking.bookingId()))
                            .orElseThrow();
                    assertThat(order.status())
                            .isEqualTo(BookingStatus.CANCELLED);
                    assertThat(order.cancellationReason())
                            .isEqualTo(CancellationReason.PAYMENT_TIMEOUT);
                });

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT status FROM transport_trip_seat
                WHERE trip_id = ? AND seat_number = ?
                """,
                String.class,
                TRIP.value(),
                "B01"
        )).isEqualTo("AVAILABLE");

        assertThat(seatInventoryRepository
                .findByTripReference(TRIP)
                .orElseThrow()
                .availableSeats()).isEqualTo(10);
    }

    private void seedTrip() {
        LocalDateTime createdAt = LocalDateTime.ofInstant(
                BASE_TIME,
                ZoneOffset.UTC
        );
        jdbcTemplate.update(
                """
                INSERT INTO transport_vehicle (
                    id, vehicle_no, license_plate, seat_count,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                9102L,
                UUID.randomUUID().toString(),
                "MSG-9101",
                10,
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
                9102L,
                UUID.randomUUID().toString(),
                "MSG-ROUTE",
                "MAIN",
                "WEST",
                45,
                "ENABLED",
                0L,
                createdAt,
                createdAt
        );
        jdbcTemplate.update(
                """
                INSERT INTO transport_trip (
                    id, trip_no, vehicle_id, route_id,
                    departure_time, booking_deadline, price,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                TRIP.value(),
                TRIP_NUMBER,
                9102L,
                9102L,
                LocalDateTime.ofInstant(
                        BASE_TIME.plusSeconds(7200),
                        ZoneOffset.UTC
                ),
                LocalDateTime.ofInstant(
                        BASE_TIME.plusSeconds(3600),
                        ZoneOffset.UTC
                ),
                new BigDecimal("5.50"),
                "OPEN_FOR_BOOKING",
                0L,
                createdAt,
                createdAt
        );
    }

    private void seedSeat(String seatNumber) {
        LocalDateTime createdAt = LocalDateTime.ofInstant(
                BASE_TIME,
                ZoneOffset.UTC
        );
        jdbcTemplate.update(
                """
                INSERT INTO transport_trip_seat (
                    trip_id, seat_number, status,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                TRIP.value(),
                seatNumber,
                "AVAILABLE",
                0L,
                createdAt,
                createdAt
        );
    }
}
