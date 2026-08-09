package com.schoolbus.payment.application;

import com.schoolbus.booking.application.booking.TripSeatReservationPort;
import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingId;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingRequestNumber;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.domain.order.SeatNumber;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.payment.domain.PaymentId;
import com.schoolbus.payment.domain.PaymentIdGenerator;
import com.schoolbus.payment.domain.PaymentNumber;
import com.schoolbus.payment.domain.PaymentRecord;
import com.schoolbus.payment.domain.PaymentRecordRepository;
import com.schoolbus.payment.domain.PaymentRequestNumber;
import com.schoolbus.shared.domain.identity.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentConfirmationTransactionTest {

    private static final Instant PLACED_AT = Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-08T00:15:00Z");
    private static final Instant NOW = Instant.parse("2026-08-08T00:10:05Z");
    private static final String BOOKING_NO = "55555555-5555-5555-5555-555555555555";
    private static final String PAYMENT_NO = "77777777-7777-7777-7777-777777777777";

    private PaymentRecordRepository paymentRepository;
    private BookingOrderRepository bookingRepository;
    private TripSeatReservationPort seatPort;
    private PaymentRefundOutboxPort outboxPort;
    private PaymentConfirmationTransaction transaction;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRecordRepository.class);
        bookingRepository = mock(BookingOrderRepository.class);
        seatPort = mock(TripSeatReservationPort.class);
        outboxPort = mock(PaymentRefundOutboxPort.class);
        PaymentIdGenerator idGenerator = () -> PaymentId.of(9001L);
        transaction = new PaymentConfirmationTransaction(
                paymentRepository,
                bookingRepository,
                seatPort,
                outboxPort,
                idGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(paymentRepository.findByRequestNumber(any())).thenReturn(Optional.empty());
        when(paymentRepository.findByPaymentNumber(any())).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldConfirmOrderAndSellSeatInOneAttempt() {
        BookingOrder order = pendingOrder();
        when(bookingRepository.findByBookingNumber(any())).thenReturn(Optional.of(order));
        when(seatPort.confirmSeatSold(any())).thenReturn(true);
        when(bookingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmPaymentResult result = transaction.confirmOnce(command(NOW.minusSeconds(5)));

        assertThat(result.outcome()).isEqualTo(PaymentConfirmationOutcome.CONFIRMED);
        assertThat(order.status()).isEqualTo(BookingStatus.PAID);
        assertThat(order.paymentReference().toString()).isEqualTo(PAYMENT_NO);
        verify(seatPort).confirmSeatSold(any());
        verify(paymentRepository).save(any());
        verify(bookingRepository).save(order);
        verify(outboxPort, never()).append(any());
    }

    @Test
    void shouldRecordRefundWhenPaymentArrivesAfterDeadline() {
        BookingOrder order = pendingOrder();
        when(bookingRepository.findByBookingNumber(any())).thenReturn(Optional.of(order));

        ConfirmPaymentResult result = transaction.confirmOnce(command(EXPIRES_AT));

        assertThat(result.outcome()).isEqualTo(PaymentConfirmationOutcome.REFUND_PENDING);
        assertThat(order.status()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        ArgumentCaptor<PaymentRecord> paymentCaptor = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().failureReason())
                .isEqualTo("PAYMENT_WINDOW_EXPIRED");
        verify(outboxPort).append(any());
        verify(seatPort, never()).confirmSeatSold(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void shouldReturnExistingPaymentForRepeatedCallback() {
        PaymentRecord existing = PaymentRecord.succeeded(
                PaymentId.of(9001L),
                PaymentNumber.of(PAYMENT_NO),
                PaymentRequestNumber.of("callback-1"),
                BookingNumber.of(BOOKING_NO),
                BookingAmount.of("5.50"),
                NOW.minusSeconds(5),
                NOW
        );
        when(paymentRepository.findByRequestNumber(any()))
                .thenReturn(Optional.of(existing));

        ConfirmPaymentResult result = transaction.confirmOnce(command(NOW.minusSeconds(5)));

        assertThat(result.paymentId()).isEqualTo(9001L);
        verify(bookingRepository, never()).findByBookingNumber(any());
        verify(paymentRepository, never()).save(any());
    }

    private ConfirmPaymentCommand command(Instant paidAt) {
        return new ConfirmPaymentCommand(
                "callback-1",
                PAYMENT_NO,
                BOOKING_NO,
                new BigDecimal("5.50"),
                paidAt
        );
    }

    private BookingOrder pendingOrder() {
        return BookingOrder.place(
                BookingId.of(5001L),
                BookingNumber.of(BOOKING_NO),
                BookingRequestNumber.of("booking-request-1"),
                UserId.of(1001L),
                TripReference.of(2001L),
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                EXPIRES_AT,
                PLACED_AT
        );
    }
}
