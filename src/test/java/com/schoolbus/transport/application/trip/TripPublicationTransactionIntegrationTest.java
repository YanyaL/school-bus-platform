package com.schoolbus.transport.application.trip;

import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingTransaction;
import com.schoolbus.booking.application.tripcancellation.TripCancellationRequestedEnvelope;
import com.schoolbus.booking.application.tripcancellation.TripCancellationRequestedMessage;
import com.schoolbus.payment.application.refund.PaymentRefundTransaction;
import com.schoolbus.payment.application.refund.PaymentRefundRequiredMessage;
import com.schoolbus.payment.application.refund.RefundMessageEnvelope;
import com.schoolbus.payment.application.refund.RefundReceipt;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration-test")
@Import(TripPublicationTransactionIntegrationTest.FixedClockConfiguration.class)
class TripPublicationTransactionIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-08-14T00:00:00Z");

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(
                    DockerImageName.parse("mysql:8.4.0")
            )
                    .withDatabaseName("school_bus_publication_test")
                    .withUsername("school_bus")
                    .withPassword("school_bus");

    @Autowired
    private TripPublicationApplicationService service;

    @Autowired
    private TripCancellationApplicationService cancellationService;

    @Autowired
    private TripCancellationBookingTransaction bookingCancellationTransaction;

    @Autowired
    private TripCancellationCompletionTransaction cancellationCompletionTransaction;

    @Autowired
    private PaymentRefundTransaction paymentRefundTransaction;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private BookableTripCache bookableTripCache;

    @BeforeEach
    void seedDraftTrip() {
        jdbcTemplate.update("DELETE FROM event_consumed");
        jdbcTemplate.update("DELETE FROM event_outbox");
        jdbcTemplate.update("DELETE FROM payment_record");
        jdbcTemplate.update("DELETE FROM booking_order");
        jdbcTemplate.update("DELETE FROM booking_trip_cancellation_saga");
        jdbcTemplate.update("DELETE FROM transport_trip_seat");
        jdbcTemplate.update("DELETE FROM booking_trip_inventory");
        jdbcTemplate.update("DELETE FROM transport_vehicle_seat");
        jdbcTemplate.update("DELETE FROM transport_trip");
        jdbcTemplate.update("DELETE FROM transport_route");
        jdbcTemplate.update("DELETE FROM transport_vehicle");

        LocalDateTime timestamp = LocalDateTime.ofInstant(
                NOW.minusSeconds(3600),
                ZoneOffset.UTC
        );
        jdbcTemplate.update(
                """
                INSERT INTO transport_vehicle (
                    id, vehicle_no, license_plate, seat_count,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                3001L,
                "11111111-1111-1111-1111-111111111111",
                "QLD123",
                3,
                "ENABLED",
                0L,
                timestamp,
                timestamp
        );
        for (String seatNumber : new String[]{"1", "2", "3"}) {
            jdbcTemplate.update(
                    """
                    INSERT INTO transport_vehicle_seat (
                        vehicle_id, seat_number, created_at
                    ) VALUES (?, ?, ?)
                    """,
                    3001L,
                    seatNumber,
                    timestamp
            );
        }
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
                "MAIN-EAST-01",
                "MAIN",
                "EAST",
                40,
                "ENABLED",
                0L,
                timestamp,
                timestamp
        );
        jdbcTemplate.update(
                """
                INSERT INTO transport_trip (
                    id, trip_no, vehicle_id, route_id,
                    departure_time, booking_deadline, price,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                5001L,
                "33333333-3333-3333-3333-333333333333",
                3001L,
                2001L,
                LocalDateTime.ofInstant(
                        NOW.plusSeconds(24 * 3600),
                        ZoneOffset.UTC
                ),
                LocalDateTime.ofInstant(
                        NOW.plusSeconds(23 * 3600),
                        ZoneOffset.UTC
                ),
                new java.math.BigDecimal("5.00"),
                "DRAFT",
                0L,
                timestamp,
                timestamp
        );
    }

    @Test
    void shouldCommitStatusSeatsInventoryAndThenEvictCache() {
        AdminTripView result = service.publish(
                new PublishTripCommand(5001L, 0L)
        );

        assertThat(result.status().name())
                .isEqualTo("OPEN_FOR_BOOKING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transport_trip WHERE id = 5001",
                String.class
        )).isEqualTo("OPEN_FOR_BOOKING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transport_trip_seat WHERE trip_id = 5001",
                Integer.class
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT available_seats FROM booking_trip_inventory WHERE trip_id = 5001",
                Integer.class
        )).isEqualTo(3);
        verify(bookableTripCache).evict();
    }

    @Test
    void shouldRollbackTripAndSeatsWhenInventoryInitializationFails() {
        LocalDateTime timestamp = LocalDateTime.ofInstant(
                NOW.minusSeconds(60),
                ZoneOffset.UTC
        );
        jdbcTemplate.update(
                """
                INSERT INTO booking_trip_inventory (
                    trip_id, total_seats, available_seats,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                5001L,
                3,
                3,
                0L,
                timestamp,
                timestamp
        );

        assertThatThrownBy(() -> service.publish(
                new PublishTripCommand(5001L, 0L)
        )).isInstanceOf(TripNotPublishableException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transport_trip WHERE id = 5001",
                String.class
        )).isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM transport_trip WHERE id = 5001",
                Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transport_trip_seat WHERE trip_id = 5001",
                Integer.class
        )).isZero();
        verify(bookableTripCache, never()).evict();
    }

    @Test
    void shouldCancelPublishedTripWithoutActiveBookings() {
        service.publish(new PublishTripCommand(5001L, 0L));

        AdminTripView result = cancellationService.cancel(
                new CancelTripCommand(5001L, 1L)
        );

        assertThat(result.status()).isEqualTo(
                com.schoolbus.transport.domain.trip.TripStatus.CANCELLED
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transport_trip WHERE id = 5001",
                String.class
        )).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM transport_trip WHERE id = 5001",
                Long.class
        )).isEqualTo(2L);
        verify(bookableTripCache, times(2)).evict();
    }

    @Test
    void shouldRunCancellationSagaForPendingBooking() {
        service.publish(new PublishTripCommand(5001L, 0L));
        LocalDateTime timestamp = LocalDateTime.ofInstant(
                NOW,
                ZoneOffset.UTC
        );
        jdbcTemplate.update(
                """
                INSERT INTO booking_order (
                    id, order_no, request_no, user_id, trip_id,
                    seat_number, price_snapshot, status, expires_at,
                    version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                7001L,
                "44444444-4444-4444-4444-444444444444",
                "cancel-guard-request",
                9001L,
                5001L,
                "1",
                new java.math.BigDecimal("5.00"),
                "PENDING_PAYMENT",
                LocalDateTime.ofInstant(
                        NOW.plusSeconds(900),
                        ZoneOffset.UTC
                ),
                0L,
                timestamp,
                timestamp
        );
        jdbcTemplate.update(
                """
                UPDATE transport_trip_seat
                SET status = 'LOCKED',
                    locked_by_order_no = ?,
                    locked_by_user_id = ?,
                    lock_expires_at = ?,
                    version = 1,
                    updated_at = ?
                WHERE trip_id = ? AND seat_number = ?
                """,
                "44444444-4444-4444-4444-444444444444",
                9001L,
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC),
                timestamp,
                5001L,
                "1"
        );
        jdbcTemplate.update(
                """
                UPDATE booking_trip_inventory
                SET available_seats = 2, version = 1, updated_at = ?
                WHERE trip_id = ?
                """,
                timestamp,
                5001L
        );

        AdminTripView pending = cancellationService.cancel(
                new CancelTripCommand(5001L, 1L)
        );

        assertThat(pending.status()).isEqualTo(
                com.schoolbus.transport.domain.trip.TripStatus.CANCELLATION_PENDING
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transport_trip WHERE id = 5001",
                String.class
        )).isEqualTo("CANCELLATION_PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE event_type = 'TripCancellationRequested'",
                Integer.class
        )).isEqualTo(1);

        bookingCancellationTransaction.process(
                new TripCancellationRequestedEnvelope(
                        "request-event-1",
                        new TripCancellationRequestedMessage(
                                5001L,
                                2L,
                                NOW
                        )
                )
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM booking_order WHERE id = 7001",
                String.class
        )).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancel_reason FROM booking_order WHERE id = 7001",
                String.class
        )).isEqualTo("TRIP_CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transport_trip_seat WHERE trip_id = 5001 AND seat_number = '1'",
                String.class
        )).isEqualTo("AVAILABLE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT available_seats FROM booking_trip_inventory WHERE trip_id = 5001",
                Integer.class
        )).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE event_type = 'TripCancellationBookingsSettled'",
                Integer.class
        )).isEqualTo(1);

        cancellationCompletionTransaction.complete(
                new TripCancellationSettledEnvelope(
                        "settled-event-1",
                        new TripCancellationSettledMessage(5001L, NOW)
                )
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transport_trip WHERE id = 5001",
                String.class
        )).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM transport_trip WHERE id = 5001",
                Long.class
        )).isEqualTo(3L);
        verify(bookableTripCache, times(3)).evict();
    }

    @Test
    void shouldRequestAndCompleteRefundForPaidBooking() {
        service.publish(new PublishTripCommand(5001L, 0L));
        LocalDateTime timestamp = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        String orderNo = "55555555-5555-5555-5555-555555555555";
        String paymentNo = "66666666-6666-6666-6666-666666666666";
        jdbcTemplate.update(
                """
                INSERT INTO booking_order (
                    id, order_no, request_no, user_id, trip_id,
                    seat_number, price_snapshot, status, expires_at,
                    payment_no, paid_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 'PAID', ?, ?, ?, 1, ?, ?)
                """,
                7002L, orderNo, "paid-cancel-request", 9002L, 5001L,
                "2", new java.math.BigDecimal("5.00"),
                LocalDateTime.ofInstant(NOW.plusSeconds(900), ZoneOffset.UTC),
                paymentNo, timestamp, timestamp, timestamp
        );
        jdbcTemplate.update(
                """
                INSERT INTO payment_record (
                    id, payment_no, request_no, order_no, amount,
                    status, completed_at, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', ?, 0, ?, ?)
                """,
                8002L, paymentNo, "payment-request-2", orderNo,
                new java.math.BigDecimal("5.00"), timestamp, timestamp, timestamp
        );
        jdbcTemplate.update(
                """
                UPDATE transport_trip_seat
                SET status = 'SOLD', locked_by_order_no = ?,
                    locked_by_user_id = ?, version = 2, updated_at = ?
                WHERE trip_id = 5001 AND seat_number = '2'
                """,
                orderNo, 9002L, timestamp
        );
        jdbcTemplate.update(
                "UPDATE booking_trip_inventory SET available_seats = 2, version = 1, updated_at = ? WHERE trip_id = 5001",
                timestamp
        );

        cancellationService.cancel(new CancelTripCommand(5001L, 1L));
        bookingCancellationTransaction.process(
                new TripCancellationRequestedEnvelope(
                        "request-event-2",
                        new TripCancellationRequestedMessage(5001L, 2L, NOW)
                )
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM booking_order WHERE id = 7002",
                String.class
        )).isEqualTo("REFUND_PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payment_record WHERE id = 8002",
                String.class
        )).isEqualTo("REFUND_PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE event_type = 'PaymentRefundRequired'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM booking_trip_cancellation_saga WHERE trip_id = 5001",
                String.class
        )).isEqualTo("PROCESSING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE event_type = 'TripCancellationBookingsSettled'",
                Integer.class
        )).isZero();

        RefundMessageEnvelope refundEnvelope = new RefundMessageEnvelope(
                "refund-event-2",
                new PaymentRefundRequiredMessage(
                        paymentNo,
                        orderNo,
                        new java.math.BigDecimal("5.00"),
                        "TRIP_CANCELLED",
                        NOW,
                        NOW
                )
        );
        paymentRefundTransaction.prepareRefund(refundEnvelope);
        paymentRefundTransaction.completeRefund(
                refundEnvelope,
                new RefundReceipt("refund-trip-cancel-2", NOW)
        );

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM booking_order WHERE id = 7002",
                String.class
        )).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payment_record WHERE id = 8002",
                String.class
        )).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM booking_trip_cancellation_saga WHERE trip_id = 5001",
                String.class
        )).isEqualTo("SETTLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_outbox WHERE event_type = 'TripCancellationBookingsSettled'",
                Integer.class
        )).isEqualTo(1);

        cancellationCompletionTransaction.complete(
                new TripCancellationSettledEnvelope(
                        "settled-event-2",
                        new TripCancellationSettledMessage(5001L, NOW)
                )
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transport_trip WHERE id = 5001",
                String.class
        )).isEqualTo("CANCELLED");
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
