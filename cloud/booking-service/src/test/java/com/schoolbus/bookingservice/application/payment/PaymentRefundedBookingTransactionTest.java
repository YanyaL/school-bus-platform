package com.schoolbus.bookingservice.application.payment;

import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationProgressPort;
import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationSettlementOutboxPort;
import com.schoolbus.bookingservice.domain.order.BookingAmount;
import com.schoolbus.bookingservice.domain.order.BookingId;
import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.domain.order.BookingOrderRepository;
import com.schoolbus.bookingservice.domain.order.BookingRequestNumber;
import com.schoolbus.bookingservice.domain.order.BookingStatus;
import com.schoolbus.bookingservice.domain.order.PaymentReference;
import com.schoolbus.bookingservice.domain.order.SeatNumber;
import com.schoolbus.bookingservice.domain.trip.PublicTripNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.application.messaging.ConsumedEventStore;
import com.schoolbus.bookingservice.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentRefundedBookingTransactionTest {

    private static final Instant NOW =
            Instant.parse("2026-08-21T12:00:00Z");
    private static final String PAYMENT_NUMBER =
            "99999999-9999-9999-9999-999999999999";
    private static final String BOOKING_NUMBER =
            "88888888-8888-8888-8888-888888888888";

    private BookingOrderRepository orderRepository;
    private ConsumedEventStore consumedEventStore;
    private TripCancellationProgressPort progressPort;
    private TripCancellationSettlementOutboxPort settlementOutboxPort;
    private PaymentRefundedBookingTransaction transaction;

    @BeforeEach
    void setUp() {
        orderRepository = mock(BookingOrderRepository.class);
        consumedEventStore = mock(ConsumedEventStore.class);
        progressPort = mock(TripCancellationProgressPort.class);
        settlementOutboxPort = mock(TripCancellationSettlementOutboxPort.class);
        transaction = new PaymentRefundedBookingTransaction(
                orderRepository,
                consumedEventStore,
                progressPort,
                settlementOutboxPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldIgnoreDuplicateEventId() {
        when(consumedEventStore.insertIfAbsent(
                PaymentRefundedBookingTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(false);

        PaymentRefundedResult result = transaction.process(envelope(
                "TRIP_CANCELLED"
        ));

        assertThat(result.outcome())
                .isEqualTo(PaymentRefundedOutcome.DUPLICATE);
        verifyNoInteractions(orderRepository, progressPort);
    }

    @Test
    void shouldNotOverwriteTerminalRefundedState() {
        BookingOrder order = refundPendingOrder("TRIP_CANCELLED");
        order.confirmRefund(NOW.minusSeconds(30));
        when(consumedEventStore.insertIfAbsent(any(), any(), any()))
                .thenReturn(true);
        when(orderRepository.findByBookingNumber(any()))
                .thenReturn(Optional.of(order));

        PaymentRefundedResult result = transaction.process(envelope(
                "TRIP_CANCELLED"
        ));

        assertThat(result.outcome())
                .isEqualTo(PaymentRefundedOutcome.ALREADY_REFUNDED);
        assertThat(order.status()).isEqualTo(BookingStatus.REFUNDED);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(progressPort, settlementOutboxPort);
    }

    @Test
    void shouldConfirmRefundAndSettleTripCancellation() {
        BookingOrder order = refundPendingOrder("TRIP_CANCELLED");
        when(consumedEventStore.insertIfAbsent(any(), any(), any()))
                .thenReturn(true);
        when(orderRepository.findByBookingNumber(any()))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
        when(progressPort.completeRefund(eq(300L), any()))
                .thenReturn(true);

        PaymentRefundedResult result = transaction.process(envelope(
                "TRIP_CANCELLED"
        ));

        assertThat(result.outcome())
                .isEqualTo(PaymentRefundedOutcome.APPLIED);
        assertThat(order.status()).isEqualTo(BookingStatus.REFUNDED);
        verify(progressPort).completeRefund(eq(300L), any());
        verify(settlementOutboxPort).append(any());
    }

    @Test
    void shouldConfirmUserCancelledRefundWithoutTripSaga() {
        BookingOrder order = refundPendingOrder("USER_CANCELLED");
        when(consumedEventStore.insertIfAbsent(any(), any(), any()))
                .thenReturn(true);
        when(orderRepository.findByBookingNumber(any()))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        PaymentRefundedResult result = transaction.process(envelope(
                "USER_CANCELLED"
        ));

        assertThat(result.outcome())
                .isEqualTo(PaymentRefundedOutcome.APPLIED);
        verifyNoInteractions(progressPort, settlementOutboxPort);
    }

    @Test
    void shouldRejectRefundForAnotherPayment() {
        BookingOrder order = refundPendingOrder("USER_CANCELLED");
        when(consumedEventStore.insertIfAbsent(any(), any(), any()))
                .thenReturn(true);
        when(orderRepository.findByBookingNumber(any()))
                .thenReturn(Optional.of(order));

        PaymentRefundedEnvelope mismatched = new PaymentRefundedEnvelope(
                "event-1",
                new PaymentRefundedMessage(
                        "77777777-7777-7777-7777-777777777777",
                        BOOKING_NUMBER,
                        "refund-001",
                        "USER_CANCELLED",
                        NOW.minusSeconds(5),
                        NOW
                )
        );

        assertThatThrownBy(() -> transaction.process(mismatched))
                .isInstanceOf(PaymentRefundedMessageConflictException.class)
                .hasMessageContaining("paymentNumber");
        assertThat(order.status()).isEqualTo(BookingStatus.REFUND_PENDING);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(progressPort, settlementOutboxPort);
    }

    @Test
    void shouldRejectRefundWithConflictingReason() {
        BookingOrder order = refundPendingOrder("USER_CANCELLED");
        when(consumedEventStore.insertIfAbsent(any(), any(), any()))
                .thenReturn(true);
        when(orderRepository.findByBookingNumber(any()))
                .thenReturn(Optional.of(order));

        assertThatThrownBy(() -> transaction.process(envelope(
                "TRIP_CANCELLED"
        )))
                .isInstanceOf(PaymentRefundedMessageConflictException.class)
                .hasMessageContaining("reason");
        assertThat(order.status()).isEqualTo(BookingStatus.REFUND_PENDING);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(progressPort, settlementOutboxPort);
    }

    private PaymentRefundedEnvelope envelope(String reason) {
        return new PaymentRefundedEnvelope(
                "event-1",
                new PaymentRefundedMessage(
                        PAYMENT_NUMBER,
                        BOOKING_NUMBER,
                        "refund-001",
                        reason,
                        NOW.minusSeconds(5),
                        NOW
                )
        );
    }

    private BookingOrder refundPendingOrder(String reason) {
        Instant placedAt = NOW.minusSeconds(600);
        Instant paidAt = NOW.minusSeconds(120);
        Instant refundRequestedAt = NOW.minusSeconds(30);
        BookingOrder order = BookingOrder.place(
                BookingId.of(100L),
                BookingNumber.of(BOOKING_NUMBER),
                BookingRequestNumber.of("request-1"),
                UserId.of(200L),
                TripReference.of(300L),
                PublicTripNumber.of(UUID.randomUUID().toString()),
                SeatNumber.of("A01"),
                BookingAmount.of("12.50"),
                NOW.plusSeconds(300),
                placedAt
        );
        order.confirmPayment(
                PaymentReference.of(PAYMENT_NUMBER),
                paidAt,
                paidAt.plusSeconds(1)
        );
        if ("TRIP_CANCELLED".equals(reason)) {
            order.requestRefundBecauseTripWasCancelled(refundRequestedAt);
        } else {
            order.requestRefundBecauseUserCancelled(refundRequestedAt);
        }
        return order;
    }
}
