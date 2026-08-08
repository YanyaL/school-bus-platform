package com.schoolbus.booking.infrastructure.persistence;

import com.schoolbus.booking.application.booking.SeatLockRequest;
import com.schoolbus.booking.application.booking.BookingApplicationService;
import com.schoolbus.booking.application.booking.BookingCreationTransaction;
import com.schoolbus.booking.application.booking.CreateBookingCommand;
import com.schoolbus.booking.application.booking.CreateBookingResult;
import com.schoolbus.booking.application.booking.TripSeatReservationPort;
import com.schoolbus.booking.domain.inventory.NoSeatAvailableException;
import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingIdGenerator;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "school-bus.booking.maximum-attempts=50"
})
@ActiveProfiles("integration-test")
@Import(BookingPersistenceIntegrationTest.FixedClockConfiguration.class)
class BookingPersistenceIntegrationTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-08-08T00:15:00Z");
    private static final TripReference TRIP =
            TripReference.of(2001L);

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
    private BookingOrderRepository bookingOrderRepository;

    @Autowired
    private SeatInventoryRepository seatInventoryRepository;

    @Autowired
    private TripSeatReservationPort tripSeatReservationPort;

    @Autowired
    private BookingApplicationService bookingApplicationService;

    @Autowired
    private BookingCreationTransaction bookingCreationTransaction;

    @Autowired
    private ControllableBookingIdGenerator bookingIdGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        bookingIdGenerator.reset();
        jdbcTemplate.update("DELETE FROM booking_order");
        jdbcTemplate.update("DELETE FROM booking_trip_inventory");
        jdbcTemplate.update("DELETE FROM transport_trip_seat");
        jdbcTemplate.update("DELETE FROM transport_trip");
        jdbcTemplate.update("DELETE FROM transport_route");
        jdbcTemplate.update("DELETE FROM transport_vehicle");
        seedTrip();
    }

    @Test
    void shouldCreateBookingAsOneTransactionAndRemainIdempotent() {
        seedSeat("C01");
        seatInventoryRepository.save(
                SeatInventory.initialize(TRIP, 10, CREATED_AT)
        );
        CreateBookingCommand command = new CreateBookingCommand(
                2001L,
                TRIP.value(),
                "C01",
                "create-booking-2001"
        );

        CreateBookingResult first =
                bookingApplicationService.createBooking(command);
        CreateBookingResult repeated =
                bookingApplicationService.createBooking(command);

        assertThat(repeated.bookingId()).isEqualTo(first.bookingId());
        assertThat(first.expiresAt())
                .isEqualTo(CREATED_AT.plusSeconds(15 * 60));
        assertThat(seatInventoryRepository
                .findByTripReference(TRIP)
                .orElseThrow()
                .availableSeats()).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_order",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM transport_trip_seat
                WHERE trip_id = ? AND seat_number = ?
                """,
                String.class,
                TRIP.value(),
                "C01"
        )).isEqualTo("LOCKED");
    }

    @Test
    void shouldRollbackSeatAndInventoryWhenOrderInsertFails() {
        seedSeat("C02");
        seatInventoryRepository.save(
                SeatInventory.initialize(TRIP, 10, CREATED_AT)
        );
        bookingOrderRepository.save(pendingOrder());
        bookingIdGenerator.forceNext(5001L);

        assertThatThrownBy(
                () -> bookingCreationTransaction.createOnce(
                        new CreateBookingCommand(
                                2002L,
                                TRIP.value(),
                                "C02",
                                "rollback-booking-2002"
                        )
                )
        ).isInstanceOf(DataAccessException.class);

        assertThat(seatInventoryRepository
                .findByTripReference(TRIP)
                .orElseThrow()
                .availableSeats()).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT status
                FROM transport_trip_seat
                WHERE trip_id = ? AND seat_number = ?
                """,
                String.class,
                TRIP.value(),
                "C02"
        )).isEqualTo("AVAILABLE");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM booking_order
                WHERE request_no = 'rollback-booking-2002'
                """,
                Integer.class
        )).isZero();
    }

    @Test
    void shouldCreateExactlyOneOrderPerSeatUnderConcurrency() {
        int capacity = 10;
        int requests = 20;
        IntStream.range(0, capacity)
                .forEach(index -> seedSeat("D" + index));
        seatInventoryRepository.save(
                SeatInventory.initialize(TRIP, capacity, CREATED_AT)
        );

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(requests)) {
            List<CompletableFuture<Boolean>> attempts =
                    IntStream.range(0, requests)
                            .mapToObj(index ->
                                    CompletableFuture.supplyAsync(
                                            () -> tryCreateBooking(index),
                                            executor
                                    )
                            )
                            .toList();
            long successes = attempts.stream()
                    .map(CompletableFuture::join)
                    .filter(Boolean::booleanValue)
                    .count();

            assertThat(successes).isEqualTo(capacity);
        }

        assertThat(seatInventoryRepository
                .findByTripReference(TRIP)
                .orElseThrow()
                .availableSeats()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking_order",
                Integer.class
        )).isEqualTo(capacity);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM transport_trip_seat
                WHERE trip_id = ? AND status = 'LOCKED'
                """,
                Integer.class,
                TRIP.value()
        )).isEqualTo(capacity);
    }

    @Test
    void shouldAtomicallyLockOnlyAvailableSeat() {
        seedSeat("A01");
        SeatLockRequest firstRequest = seatLockRequest(
                "A01",
                "55555555-5555-5555-5555-555555555555",
                1001L
        );
        SeatLockRequest secondRequest = seatLockRequest(
                "A01",
                "66666666-6666-6666-6666-666666666666",
                1002L
        );

        assertThat(tripSeatReservationPort.tryLockSeat(firstRequest))
                .isTrue();
        assertThat(tripSeatReservationPort.tryLockSeat(secondRequest))
                .isFalse();

        var lockedSeat = jdbcTemplate.queryForMap(
                """
                SELECT status, locked_by_order_no,
                       locked_by_user_id, version
                FROM transport_trip_seat
                WHERE trip_id = ? AND seat_number = ?
                """,
                TRIP.value(),
                "A01"
        );
        assertThat(lockedSeat.get("status")).isEqualTo("LOCKED");
        assertThat(lockedSeat.get("locked_by_order_no"))
                .isEqualTo(
                        "55555555-5555-5555-5555-555555555555"
                );
        assertThat(((Number) lockedSeat.get("locked_by_user_id"))
                .longValue()).isEqualTo(1001L);
        assertThat(((Number) lockedSeat.get("version"))
                .longValue()).isEqualTo(1L);
    }

    @Test
    void shouldAllowOnlyOneConcurrentLockForSameSeat() {
        seedSeat("B01");
        int requests = 20;

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(requests)) {
            List<CompletableFuture<Boolean>> attempts =
                    IntStream.range(0, requests)
                            .mapToObj(index ->
                                    CompletableFuture.supplyAsync(
                                            () -> tripSeatReservationPort
                                                    .tryLockSeat(
                                                            seatLockRequest(
                                                                    "B01",
                                                                    UUID.randomUUID().toString(),
                                                                    1100L + index
                                                            )
                                                    ),
                                            executor
                                    )
                            )
                            .toList();
            long successes = attempts.stream()
                    .map(CompletableFuture::join)
                    .filter(Boolean::booleanValue)
                    .count();

            assertThat(successes).isEqualTo(1L);
            Integer version = jdbcTemplate.queryForObject(
                    """
                    SELECT version
                    FROM transport_trip_seat
                    WHERE trip_id = ? AND seat_number = ?
                    """,
                    Integer.class,
                    TRIP.value(),
                    "B01"
            );
            assertThat(version).isEqualTo(1);
        }
    }

    @Test
    void shouldPersistAndRestoreBookingOrder() {
        BookingOrder order = pendingOrder();

        bookingOrderRepository.save(order);
        BookingOrder restored = bookingOrderRepository
                .findById(order.bookingId())
                .orElseThrow();

        assertThat(restored.bookingId()).isEqualTo(order.bookingId());
        assertThat(restored.bookingNumber())
                .isEqualTo(order.bookingNumber());
        assertThat(restored.amount())
                .isEqualTo(BookingAmount.of("5.50"));
        assertThat(restored.status())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        BookingOrder foundByRequestNumber = bookingOrderRepository
                .findByRequestNumber(
                        BookingRequestNumber.of("request-5001")
                )
                .orElseThrow();
        assertThat(foundByRequestNumber.bookingId())
                .isEqualTo(restored.bookingId());
        assertThat(
                bookingOrderRepository
                        .existsActiveByUserIdAndTripReference(
                                UserId.of(1001L),
                                TRIP
                        )
        ).isTrue();
    }

    @Test
    void shouldRejectStaleInventoryUpdate() {
        seatInventoryRepository.save(
                SeatInventory.initialize(TRIP, 10, CREATED_AT)
        );
        SeatInventory first = seatInventoryRepository
                .findByTripReference(TRIP)
                .orElseThrow();
        SeatInventory stale = seatInventoryRepository
                .findByTripReference(TRIP)
                .orElseThrow();

        first.reserve(CREATED_AT.plusSeconds(60));
        seatInventoryRepository.save(first);

        stale.reserve(CREATED_AT.plusSeconds(60));
        assertThatThrownBy(
                () -> seatInventoryRepository.save(stale)
        ).isInstanceOf(OptimisticLockingFailureException.class);

        SeatInventory persisted = seatInventoryRepository
                .findByTripReference(TRIP)
                .orElseThrow();
        assertThat(persisted.availableSeats()).isEqualTo(9);
        assertThat(persisted.version()).isEqualTo(1L);
    }

    @Test
    void shouldNeverOversellUnderConcurrentReservations() {
        int capacity = 10;
        int requests = 50;
        seatInventoryRepository.save(
                SeatInventory.initialize(
                        TRIP,
                        capacity,
                        CREATED_AT
                )
        );

        try (ExecutorService executor =
                     Executors.newFixedThreadPool(20)) {
            List<CompletableFuture<Boolean>> attempts =
                    IntStream.range(0, requests)
                            .mapToObj(ignored ->
                                    CompletableFuture.supplyAsync(
                                            this::tryReserveWithRetry,
                                            executor
                                    )
                            )
                            .toList();
            long successes = attempts.stream()
                    .map(CompletableFuture::join)
                    .filter(Boolean::booleanValue)
                    .count();

            SeatInventory persisted = seatInventoryRepository
                    .findByTripReference(TRIP)
                    .orElseThrow();
            assertThat(successes).isEqualTo(capacity);
            assertThat(persisted.availableSeats()).isZero();
            assertThat(persisted.version()).isEqualTo(capacity);
        }
    }

    private boolean tryReserveWithRetry() {
        for (int attempt = 0; attempt < 100; attempt++) {
            SeatInventory inventory = seatInventoryRepository
                    .findByTripReference(TRIP)
                    .orElseThrow();
            try {
                inventory.reserve(CREATED_AT.plusSeconds(60));
            } catch (NoSeatAvailableException exception) {
                return false;
            }
            try {
                seatInventoryRepository.save(inventory);
                return true;
            } catch (OptimisticLockingFailureException exception) {
                Thread.onSpinWait();
            }
        }
        throw new IllegalStateException(
                "inventory update did not converge after retries"
        );
    }

    private boolean tryCreateBooking(int index) {
        try {
            bookingApplicationService.createBooking(
                    new CreateBookingCommand(
                            3000L + index,
                            TRIP.value(),
                            "D" + (index % 10),
                            "concurrent-booking-" + index
                    )
            );
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private BookingOrder pendingOrder() {
        return BookingOrder.place(
                BookingId.of(5001L),
                BookingNumber.of(
                        "55555555-5555-5555-5555-555555555555"
                ),
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TRIP,
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                EXPIRES_AT,
                CREATED_AT
        );
    }

    private SeatLockRequest seatLockRequest(
            String seatNumber,
            String bookingNumber,
            long userId
    ) {
        return new SeatLockRequest(
                TRIP,
                SeatNumber.of(seatNumber),
                BookingNumber.of(bookingNumber),
                UserId.of(userId),
                EXPIRES_AT,
                CREATED_AT
        );
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
                "BOOK-001",
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
                "BOOKING-ROUTE",
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
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock bookingTestClock() {
            return Clock.fixed(CREATED_AT, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ControllableBookingIdGenerator bookingTestIdGenerator() {
            return new ControllableBookingIdGenerator();
        }
    }

    static final class ControllableBookingIdGenerator
            implements BookingIdGenerator {

        private static final long INITIAL_ID = 9_000_000L;

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
