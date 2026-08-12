package com.schoolbus.booking.infrastructure.messaging;

import com.schoolbus.booking.application.booking.BookingApplicationService;
import com.schoolbus.booking.application.booking.CreateBookingCommand;
import com.schoolbus.booking.application.booking.CreateBookingResult;
import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingIdGenerator;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.booking.infrastructure.outbox.BookingExpirationOutboxRelay;
import com.schoolbus.testsupport.EnabledIfMessagingAcceptance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "school-bus.booking.payment-window=PT3S",
        "school-bus.booking.maximum-attempts=50",
        "school-bus.messaging.booking-expiration.delay-exchange=schoolbus.booking.expiration.delay.messaging-it",
        "school-bus.messaging.booking-expiration.delay-routing-key=booking.payment.deadline.delay.messaging-it",
        "school-bus.messaging.booking-expiration.delay-queue=schoolbus.booking.expiration.delay.messaging-it",
        "school-bus.messaging.booking-expiration.processing-exchange=schoolbus.booking.events.messaging-it",
        "school-bus.messaging.booking-expiration.processing-routing-key=booking.payment.deadline.reached.messaging-it",
        "school-bus.messaging.booking-expiration.processing-queue=schoolbus.booking.expiration.messaging-it",
        "school-bus.messaging.booking-expiration.dead-letter-exchange=schoolbus.booking.dlx.messaging-it",
        "school-bus.messaging.booking-expiration.dead-letter-routing-key=booking.expiration.dead.messaging-it",
        "school-bus.messaging.booking-expiration.dead-letter-queue=schoolbus.booking.expiration.dlq.messaging-it"
})
@ActiveProfiles({"integration-test", "local"})
@Import(BookingExpirationMessagingIntegrationTest.TestClockConfiguration.class)
@EnabledIfMessagingAcceptance
class BookingExpirationMessagingIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final TripReference TRIP =
            TripReference.of(2001L);
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
    private MutableClock mutableClock;

    @Autowired
    private ControllableBookingIdGenerator bookingIdGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        mutableClock.reset();
        bookingIdGenerator.reset();
        jdbcTemplate.update("DELETE FROM event_outbox");
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
    void shouldExpireBookingThroughOutboxRelayAndRabbitMqListener() {
        seedSeat("M01");
        seatInventoryRepository.save(
                SeatInventory.initialize(TRIP, 10, CREATED_AT)
        );

        CreateBookingResult booking = bookingApplicationService.createBooking(
                new CreateBookingCommand(
                        7001L,
                        TRIP.value(),
                        "M01",
                        "messaging-booking-7001"
                )
        );

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM event_outbox
                WHERE event_type = 'BookingPaymentDeadlineReached'
                  AND status = 'NEW'
                """,
                Integer.class
        )).isEqualTo(1);

        Instant expiresAt = jdbcTemplate.queryForObject(
                """
                SELECT payment_expires_at FROM booking_order
                WHERE id = ?
                """,
                Timestamp.class,
                booking.bookingId()
        ).toInstant();
        mutableClock.set(expiresAt);

        assertThat(outboxRelay.relayReadyEvents().published()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT status FROM event_outbox
                WHERE event_type = 'BookingPaymentDeadlineReached'
                """,
                String.class
        )).isEqualTo("PUBLISHED");

        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(
                        bookingOrderRepository
                                .findById(BookingId.of(booking.bookingId()))
                                .orElseThrow()
                                .status()
                ).isEqualTo(BookingStatus.CANCELLED));

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT status FROM transport_trip_seat
                WHERE trip_id = ? AND seat_number = ?
                """,
                String.class,
                TRIP.value(),
                "M01"
        )).isEqualTo("AVAILABLE");
        assertThat(seatInventoryRepository.findByTripReference(TRIP)
                .orElseThrow()
                .availableSeats()).isEqualTo(10);
    }

    private void seedSeat(String seatNumber) {
        Timestamp createdAt = Timestamp.from(CREATED_AT);
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

    private void seedTrip() {
        Timestamp createdAt = Timestamp.from(CREATED_AT);
        jdbcTemplate.update(
                """
                INSERT INTO transport_vehicle (
                    id, vehicle_no, license_plate, seat_count,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                3001L,
                UUID.randomUUID().toString(),
                "MSG-001",
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
                2001L,
                UUID.randomUUID().toString(),
                "MSG-ROUTE",
                "MAIN",
                "EAST",
                60,
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
                UUID.randomUUID().toString(),
                3001L,
                2001L,
                Timestamp.from(CREATED_AT.plusSeconds(7200)),
                Timestamp.from(CREATED_AT.plusSeconds(3600)),
                new BigDecimal("5.50"),
                "OPEN_FOR_BOOKING",
                0L,
                createdAt,
                createdAt
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableClock messagingTestClock() {
            return new MutableClock(CREATED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ControllableBookingIdGenerator messagingTestIdGenerator() {
            return new ControllableBookingIdGenerator();
        }
    }

    static final class MutableClock extends Clock {

        private final Instant initialInstant;
        private final AtomicReference<Instant> currentInstant;
        private final ZoneId zone;

        MutableClock(Instant initialInstant, ZoneId zone) {
            this.initialInstant = initialInstant;
            this.currentInstant = new AtomicReference<>(initialInstant);
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(currentInstant.get(), requestedZone);
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }

        void set(Instant instant) {
            currentInstant.set(instant);
        }

        void reset() {
            currentInstant.set(initialInstant);
        }
    }

    static final class ControllableBookingIdGenerator
            implements BookingIdGenerator {

        private static final long INITIAL_ID = 9_100_000L;

        private final AtomicLong sequence =
                new AtomicLong(INITIAL_ID);
        private final AtomicLong forcedId = new AtomicLong();

        @Override
        public BookingId nextId() {
            long forced = forcedId.getAndSet(0L);
            return BookingId.of(
                    forced > 0L
                            ? forced
                            : sequence.incrementAndGet()
            );
        }

        void forceNext(long value) {
            forcedId.set(value);
        }

        void reset() {
            sequence.set(INITIAL_ID);
            forcedId.set(0L);
        }
    }
}
