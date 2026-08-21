package com.schoolbus.payment.application.refund;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.payment.domain.PaymentId;
import com.schoolbus.payment.domain.PaymentNumber;
import com.schoolbus.payment.domain.PaymentRecord;
import com.schoolbus.payment.domain.PaymentRecordRepository;
import com.schoolbus.payment.domain.PaymentRequestNumber;
import com.schoolbus.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRefundTransactionTest {

    private static final Instant PAID_AT = Instant.parse(
            "2026-08-10T09:50:00Z"
    );
    private static final Instant NOW = Instant.parse(
            "2026-08-10T10:00:00Z"
    );
    private static final String PAYMENT_NO =
            "77777777-7777-7777-7777-777777777777";
    private static final String BOOKING_NO =
            "55555555-5555-5555-5555-555555555555";

    private PaymentRecordRepository paymentRepository;
    private ConsumedEventRepository consumedRepository;
    private RefundedBookingPort refundedBookingPort;
    private PaymentRefundTransaction transaction;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRecordRepository.class);
        consumedRepository = mock(ConsumedEventRepository.class);
        refundedBookingPort = mock(RefundedBookingPort.class);
        transaction = new PaymentRefundTransaction(
                paymentRepository,
                consumedRepository,
                refundedBookingPort,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldValidateBeforeExternalRefundIsRequested() {
        when(paymentRepository.findByPaymentNumber(any()))
                .thenReturn(Optional.of(refundPending()));

        RefundPreparation preparation = transaction.prepareRefund(
                envelope()
        );

        assertThat(preparation.alreadyRefunded()).isFalse();
    }

    @Test
    void shouldCompleteCompensatingPaymentRefundWithoutChangingBooking() {
        PaymentRecord payment = refundPending();
        when(consumedRepository.insertIfAbsent(
                PaymentRefundTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(true);
        when(paymentRepository.findByPaymentNumber(any()))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        RefundProcessingResult result = transaction.completeRefund(
                envelope(),
                new RefundReceipt("refund-001", NOW)
        );

        assertThat(result.outcome())
                .isEqualTo(RefundProcessingOutcome.REFUNDED);
        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        verify(consumedRepository).insertIfAbsent(
                PaymentRefundTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        );
        verify(paymentRepository).save(payment);
        verify(refundedBookingPort, never()).markRefunded(any(), any());
    }

    @Test
    void shouldMarkBookingRefundedForTripCancellation() {
        PaymentRecord payment = refundPending("TRIP_CANCELLED");
        when(consumedRepository.insertIfAbsent(any(), any(), any()))
                .thenReturn(true);
        when(paymentRepository.findByPaymentNumber(any()))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        transaction.completeRefund(
                envelope("TRIP_CANCELLED"),
                new RefundReceipt("refund-001", NOW)
        );

        verify(refundedBookingPort).markRefunded(
                BookingNumber.of(BOOKING_NO),
                NOW
        );
    }

    @Test
    void shouldReturnWithoutUpdatingForDuplicateEvent() {
        when(consumedRepository.insertIfAbsent(
                PaymentRefundTransaction.CONSUMER_NAME,
                "event-1",
                NOW
        )).thenReturn(false);

        RefundProcessingResult result = transaction.completeRefund(
                envelope(),
                new RefundReceipt("refund-001", NOW)
        );

        assertThat(result.outcome())
                .isEqualTo(RefundProcessingOutcome.DUPLICATE_EVENT);
        verify(paymentRepository, never()).findByPaymentNumber(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void shouldRejectMessageThatConflictsWithPaymentRecord() {
        PaymentRecord payment = refundPending();
        when(paymentRepository.findByPaymentNumber(any()))
                .thenReturn(Optional.of(payment));
        RefundMessageEnvelope conflicting = new RefundMessageEnvelope(
                "event-1",
                new PaymentRefundRequiredMessage(
                        PAYMENT_NO,
                        BOOKING_NO,
                        new BigDecimal("6.00"),
                        "PAYMENT_WINDOW_EXPIRED",
                        PAID_AT,
                        NOW.minusSeconds(30)
                )
        );

        assertThatThrownBy(
                () -> transaction.prepareRefund(conflicting)
        ).isInstanceOf(RefundMessageConflictException.class);
    }

    private RefundMessageEnvelope envelope() {
        return envelope("PAYMENT_WINDOW_EXPIRED");
    }

    private RefundMessageEnvelope envelope(String reason) {
        return new RefundMessageEnvelope(
                "event-1",
                new PaymentRefundRequiredMessage(
                        PAYMENT_NO,
                        BOOKING_NO,
                        new BigDecimal("5.50"),
                        reason,
                        PAID_AT,
                        NOW.minusSeconds(30)
                )
        );
    }

    private PaymentRecord refundPending() {
        return refundPending("PAYMENT_WINDOW_EXPIRED");
    }

    private PaymentRecord refundPending(String reason) {
        return PaymentRecord.refundPending(
                PaymentId.of(9001L),
                PaymentNumber.of(PAYMENT_NO),
                PaymentRequestNumber.of("callback-1"),
                BookingNumber.of(BOOKING_NO),
                BookingAmount.of("5.50"),
                reason,
                PAID_AT,
                NOW.minusSeconds(20)
        );
    }
}
