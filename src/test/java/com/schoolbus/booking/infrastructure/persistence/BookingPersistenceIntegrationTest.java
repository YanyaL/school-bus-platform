package com.schoolbus.booking.infrastructure.persistence;

import com.schoolbus.booking.domain.inventory.NoSeatAvailableException;
import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration-test")
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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.update("DELETE FROM booking_order");
        jdbcTemplate.update("DELETE FROM booking_trip_inventory");
        jdbcTemplate.update("DELETE FROM transport_trip");
        jdbcTemplate.update("DELETE FROM transport_route");
        jdbcTemplate.update("DELETE FROM transport_vehicle");
        seedTrip();
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
}
