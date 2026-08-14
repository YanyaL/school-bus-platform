package com.schoolbus.booking.domain.order;

import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingOrderTest {

    private static final Instant PLACED_AT =
            Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-08-08T00:15:00Z");

    @Test
    void shouldPlacePendingPaymentOrderWithPriceSnapshot() {
        BookingOrder bookingOrder = pendingOrder();

        assertThat(bookingOrder.bookingId())
                .isEqualTo(BookingId.of(5001L));
        assertThat(bookingOrder.bookingNumber().toString())
                .isEqualTo(
                        "55555555-5555-5555-5555-555555555555"
                );
        assertThat(bookingOrder.requestNumber().value())
                .isEqualTo("request-5001");
        assertThat(bookingOrder.userId())
                .isEqualTo(UserId.of(1001L));
        assertThat(bookingOrder.tripReference())
                .isEqualTo(TripReference.of(2001L));
        assertThat(bookingOrder.seatNumber())
                .isEqualTo(SeatNumber.of("A01"));
        assertThat(bookingOrder.amount())
                .isEqualTo(BookingAmount.of("5.50"));
        assertThat(bookingOrder.status())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(bookingOrder.version()).isZero();
        assertThat(bookingOrder.createdAt()).isEqualTo(PLACED_AT);
        assertThat(bookingOrder.updatedAt()).isEqualTo(PLACED_AT);
    }

    @Test
    void shouldCancelPendingPaymentOrder() {
        BookingOrder bookingOrder = pendingOrder();
        Instant cancelledAt = PLACED_AT.plusSeconds(60);

        bookingOrder.cancel(cancelledAt);

        assertThat(bookingOrder.status())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingOrder.version()).isEqualTo(1L);
        assertThat(bookingOrder.updatedAt()).isEqualTo(cancelledAt);
        assertThat(bookingOrder.cancelledAt()).isEqualTo(cancelledAt);
        assertThat(bookingOrder.cancellationReason())
                .isEqualTo(CancellationReason.USER_CANCELLED);
    }

    @Test
    void shouldCancelPendingOrderBecauseTripWasCancelled() {
        BookingOrder order = pendingOrder();
        Instant cancelledAt = PLACED_AT.plusSeconds(60);

        order.cancelBecauseTripWasCancelled(cancelledAt);

        assertThat(order.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(order.cancellationReason())
                .isEqualTo(CancellationReason.TRIP_CANCELLED);
        assertThat(order.cancelledAt()).isEqualTo(cancelledAt);
    }

    @Test
    void shouldMovePaidOrderThroughRefundLifecycle() {
        BookingOrder order = pendingOrder();
        Instant paidAt = PLACED_AT.plusSeconds(60);
        Instant confirmedAt = paidAt.plusSeconds(1);
        order.confirmPayment(
                PaymentReference.of(
                        "77777777-7777-7777-7777-777777777777"
                ),
                paidAt,
                confirmedAt
        );

        Instant requestedAt = confirmedAt.plusSeconds(1);
        order.requestRefundBecauseTripWasCancelled(requestedAt);
        assertThat(order.status())
                .isEqualTo(BookingStatus.REFUND_PENDING);
        assertThat(order.cancellationReason())
                .isEqualTo(CancellationReason.TRIP_CANCELLED);

        Instant refundedAt = requestedAt.plusSeconds(1);
        order.confirmRefund(refundedAt);

        assertThat(order.status()).isEqualTo(BookingStatus.REFUNDED);
        assertThat(order.updatedAt()).isEqualTo(refundedAt);
        assertThat(order.version()).isEqualTo(3L);
    }

    @Test
    void shouldRejectRepeatedCancellationWithoutMutation() {
        BookingOrder bookingOrder = pendingOrder();
        Instant cancelledAt = PLACED_AT.plusSeconds(60);
        bookingOrder.cancel(cancelledAt);

        assertThatThrownBy(
                () -> bookingOrder.cancel(
                        cancelledAt.plusSeconds(60)
                )
        )
                .isInstanceOf(
                        InvalidBookingStateTransitionException.class
                )
                .hasMessage(
                        "cannot change booking status from "
                                + "CANCELLED to CANCELLED"
                );

        assertThat(bookingOrder.version()).isEqualTo(1L);
        assertThat(bookingOrder.updatedAt()).isEqualTo(cancelledAt);
    }

    @Test
    void shouldRecognizePaymentExpirationAtBoundary() {
        BookingOrder bookingOrder = pendingOrder();

        assertThat(
                bookingOrder.isPaymentExpiredAt(
                        EXPIRES_AT.minusMillis(1)
                )
        ).isFalse();
        assertThat(bookingOrder.isPaymentExpiredAt(EXPIRES_AT))
                .isTrue();
        assertThat(
                bookingOrder.isPaymentExpiredAt(
                        EXPIRES_AT.plusMillis(1)
                )
        ).isTrue();
    }

    @Test
    void shouldExpirePendingPaymentOrderAtBoundary() {
        BookingOrder bookingOrder = pendingOrder();

        bookingOrder.expire(EXPIRES_AT);

        assertThat(bookingOrder.status())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingOrder.cancellationReason())
                .isEqualTo(CancellationReason.PAYMENT_TIMEOUT);
        assertThat(bookingOrder.cancelledAt()).isEqualTo(EXPIRES_AT);
        assertThat(bookingOrder.updatedAt()).isEqualTo(EXPIRES_AT);
        assertThat(bookingOrder.version()).isEqualTo(1L);
    }

    @Test
    void shouldRejectExpirationBeforePaymentDeadline() {
        BookingOrder bookingOrder = pendingOrder();

        assertThatThrownBy(
                () -> bookingOrder.expire(EXPIRES_AT.minusMillis(1))
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("booking payment window has not expired");

        assertThat(bookingOrder.status())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(bookingOrder.version()).isZero();
    }

    @Test
    void shouldNotTreatCancelledOrderAsPaymentExpired() {
        BookingOrder bookingOrder = pendingOrder();
        bookingOrder.cancel(PLACED_AT.plusSeconds(60));

        assertThat(bookingOrder.isPaymentExpiredAt(EXPIRES_AT))
                .isFalse();
    }

    @Test
    void shouldRejectExpirationAtOrBeforePlacement() {
        assertThatThrownBy(
                () -> BookingOrder.place(
                        BookingId.of(5001L),
                        bookingNumber(),
                        BookingRequestNumber.of("request-5001"),
                        UserId.of(1001L),
                        TripReference.of(2001L),
                        SeatNumber.of("A01"),
                        BookingAmount.of("5.50"),
                        PLACED_AT,
                        PLACED_AT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expiresAt must be after createdAt");
    }

    @Test
    void shouldRejectOutOfOrderCancellationWithoutMutation() {
        BookingOrder bookingOrder = pendingOrder();

        assertThatThrownBy(
                () -> bookingOrder.cancel(
                        PLACED_AT.minusSeconds(1)
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "changedAt must not be before updatedAt"
                );

        assertThat(bookingOrder.status())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(bookingOrder.version()).isZero();
    }

    @Test
    void shouldRestorePersistedOrder() {
        Instant paidAt = PLACED_AT.plusSeconds(60);
        BookingOrder bookingOrder = BookingOrder.restore(
                BookingId.of(5001L),
                bookingNumber(),
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TripReference.of(2001L),
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                BookingStatus.PAID,
                EXPIRES_AT,
                PaymentReference.of(
                        "77777777-7777-7777-7777-777777777777"
                ),
                paidAt,
                null,
                null,
                3L,
                PLACED_AT,
                PLACED_AT.plusSeconds(120)
        );

        assertThat(bookingOrder.status())
                .isEqualTo(BookingStatus.PAID);
        assertThat(bookingOrder.paidAt()).isEqualTo(paidAt);
        assertThat(bookingOrder.version()).isEqualTo(3L);
    }

    @Test
    void shouldConfirmPaymentBeforeDeadline() {
        BookingOrder bookingOrder = pendingOrder();
        PaymentReference paymentReference = PaymentReference.of(
                "77777777-7777-7777-7777-777777777777"
        );
        Instant paidAt = EXPIRES_AT.minusSeconds(1);
        Instant confirmedAt = EXPIRES_AT.plusSeconds(5);

        bookingOrder.confirmPayment(
                paymentReference,
                paidAt,
                confirmedAt
        );

        assertThat(bookingOrder.status()).isEqualTo(BookingStatus.PAID);
        assertThat(bookingOrder.paymentReference()).isEqualTo(paymentReference);
        assertThat(bookingOrder.paidAt()).isEqualTo(paidAt);
        assertThat(bookingOrder.updatedAt()).isEqualTo(confirmedAt);
        assertThat(bookingOrder.version()).isEqualTo(1L);
    }

    @Test
    void shouldRejectPaymentAtExpirationBoundary() {
        BookingOrder bookingOrder = pendingOrder();

        assertThatThrownBy(() -> bookingOrder.confirmPayment(
                PaymentReference.of(
                        "77777777-7777-7777-7777-777777777777"
                ),
                EXPIRES_AT,
                EXPIRES_AT
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("booking payment window has expired");

        assertThat(bookingOrder.status())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(bookingOrder.version()).isZero();
    }

    @Test
    void shouldRejectInvalidValueObjects() {
        assertThatThrownBy(() -> BookingId.of(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bookingId must be positive");
        assertThatThrownBy(() -> TripReference.of(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tripReference must be positive");
        assertThatThrownBy(() -> BookingAmount.of("-0.01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("amount must not be negative");
    }

    private BookingOrder pendingOrder() {
        return BookingOrder.place(
                BookingId.of(5001L),
                bookingNumber(),
                BookingRequestNumber.of("request-5001"),
                UserId.of(1001L),
                TripReference.of(2001L),
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                EXPIRES_AT,
                PLACED_AT
        );
    }

    private BookingNumber bookingNumber() {
        return BookingNumber.of(
                "55555555-5555-5555-5555-555555555555"
        );
    }
}
