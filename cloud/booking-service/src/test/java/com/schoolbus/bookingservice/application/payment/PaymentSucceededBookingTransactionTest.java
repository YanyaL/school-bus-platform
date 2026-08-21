package com.schoolbus.bookingservice.application.payment;

import com.schoolbus.bookingservice.application.booking.TripSeatReservationPort;
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
import com.schoolbus.bookingservice.support.payment.application.PaymentRefundOutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentSucceededBookingTransactionTest {

    private static final Instant NOW =
            Instant.parse("2026-08-21T10:00:00Z");
    private static final String PAYMENT_NUMBER =
            "99999999-9999-9999-9999-999999999999";
    private static final String BOOKING_NUMBER =
            "88888888-8888-8888-8888-888888888888";

    private BookingOrderRepository orderRepository;
    private TripSeatReservationPort seatReservationPort;
    private ConsumedEventStore consumedEventStore;
    private PaymentRefundOutboxPort refundOutbox;
    private PaymentSucceededBookingTransaction transaction;

    @BeforeEach
    void setUp() {
        orderRepository = mock(BookingOrderRepository.class);
        seatReservationPort = mock(TripSeatReservationPort.class);
        consumedEventStore = mock(ConsumedEventStore.class);
        refundOutbox = mock(PaymentRefundOutboxPort.class);
        transaction = new PaymentSucceededBookingTransaction(
                orderRepository,
                seatReservationPort,
                consumedEventStore,
                refundOutbox,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldApplyPaymentSucceededToPendingBooking() {
        BookingOrder order = pendingOrder();
        when(consumedEventStore.insertIfAbsent(
                PaymentSucceededBookingTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(true);
        when(orderRepository.findByBookingNumber(any()))
                .thenReturn(Optional.of(order));
        when(seatReservationPort.confirmSeatSold(any())).thenReturn(true);
        when(orderRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        PaymentSucceededResult result = transaction.process(envelope());

        assertThat(result.outcome())
                .isEqualTo(PaymentSucceededOutcome.APPLIED);
        assertThat(order.status()).isEqualTo(BookingStatus.PAID);
        assertThat(order.paymentReference())
                .isEqualTo(PaymentReference.of(PAYMENT_NUMBER));
        verify(seatReservationPort).confirmSeatSold(any());
        verify(orderRepository).save(order);
    }

    @Test
    void shouldTreatAlreadyPaidBookingAsCompatibleShadowDelivery() {
        BookingOrder order = pendingOrder();
        order.confirmPayment(
                PaymentReference.of(PAYMENT_NUMBER),
                NOW.minusSeconds(10),
                NOW.minusSeconds(5)
        );
        when(consumedEventStore.insertIfAbsent(
                PaymentSucceededBookingTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(true);
        when(orderRepository.findByBookingNumber(any()))
                .thenReturn(Optional.of(order));

        PaymentSucceededResult result = transaction.process(envelope());

        assertThat(result.outcome())
                .isEqualTo(PaymentSucceededOutcome.ALREADY_APPLIED);
        verifyNoInteractions(seatReservationPort);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreDuplicateEventBeforeTouchingBooking() {
        when(consumedEventStore.insertIfAbsent(
                PaymentSucceededBookingTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(false);

        PaymentSucceededResult result = transaction.process(envelope());

        assertThat(result.outcome())
                .isEqualTo(PaymentSucceededOutcome.DUPLICATE);
        verifyNoInteractions(orderRepository, seatReservationPort);
    }

    @Test
    void shouldRequestRefundForAmountConflict() {
        when(consumedEventStore.insertIfAbsent(
                PaymentSucceededBookingTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(true);
        when(orderRepository.findByBookingNumber(any()))
                .thenReturn(Optional.of(pendingOrder()));
        PaymentSucceededMessage conflicting = new PaymentSucceededMessage(
                1,
                PAYMENT_NUMBER,
                BOOKING_NUMBER,
                new BigDecimal("99.00"),
                NOW.minusSeconds(10),
                NOW
        );

        PaymentSucceededResult result = transaction.process(
                new PaymentSucceededEnvelope("event-1", conflicting)
        );

        assertThat(result.outcome())
                .isEqualTo(PaymentSucceededOutcome.REFUND_REQUIRED);
        verify(refundOutbox).append(org.mockito.ArgumentMatchers.argThat(
                event -> event.reason().equals("PAYMENT_AMOUNT_MISMATCH")
        ));
        verifyNoInteractions(seatReservationPort);
    }

    @Test
    void shouldRequestRefundWhenBookingDoesNotExist() {
        when(consumedEventStore.insertIfAbsent(
                PaymentSucceededBookingTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(true);
        when(orderRepository.findByBookingNumber(any()))
                .thenReturn(Optional.empty());

        PaymentSucceededResult result = transaction.process(envelope());

        assertThat(result.outcome())
                .isEqualTo(PaymentSucceededOutcome.REFUND_REQUIRED);
        verify(refundOutbox).append(org.mockito.ArgumentMatchers.argThat(
                event -> event.reason().equals("BOOKING_NOT_FOUND")
        ));
        verifyNoInteractions(seatReservationPort);
    }

    private PaymentSucceededEnvelope envelope() {
        return new PaymentSucceededEnvelope(
                "event-1",
                new PaymentSucceededMessage(
                        1,
                        PAYMENT_NUMBER,
                        BOOKING_NUMBER,
                        new BigDecimal("12.50"),
                        NOW.minusSeconds(10),
                        NOW
                )
        );
    }

    private BookingOrder pendingOrder() {
        return BookingOrder.place(
                BookingId.of(100L),
                BookingNumber.of(BOOKING_NUMBER),
                BookingRequestNumber.of("request-1"),
                UserId.of(200L),
                TripReference.of(300L),
                PublicTripNumber.of(UUID.randomUUID().toString()),
                SeatNumber.of("A01"),
                BookingAmount.of("12.50"),
                NOW.plusSeconds(300),
                NOW.minusSeconds(100)
        );
    }
}
