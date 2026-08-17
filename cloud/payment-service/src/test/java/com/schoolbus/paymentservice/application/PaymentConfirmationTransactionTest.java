package com.schoolbus.paymentservice.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolbus.paymentservice.api.PaymentServiceException;
import com.schoolbus.paymentservice.infrastructure.identity.SnowflakeIdGenerator;
import com.schoolbus.paymentservice.infrastructure.persistence.BookingPaymentRow;
import com.schoolbus.paymentservice.infrastructure.persistence.PaymentConfirmationMapper;
import com.schoolbus.paymentservice.infrastructure.persistence.PaymentRecordRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentConfirmationTransactionTest {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");
    private static final String PAYMENT_NO = UUID.randomUUID().toString();
    private static final String BOOKING_NO = UUID.randomUUID().toString();

    private PaymentConfirmationMapper mapper;
    private PaymentConfirmationTransaction transaction;

    @BeforeEach
    void setUp() {
        mapper = mock(PaymentConfirmationMapper.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        transaction = new PaymentConfirmationTransaction(
                mapper,
                new SnowflakeIdGenerator(2, clock),
                clock,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void confirmsSeatPaymentAndBookingInOneAttempt() {
        ConfirmPaymentCommand command = command(NOW.minusSeconds(10));
        when(mapper.selectBookingForUpdate(BOOKING_NO))
                .thenReturn(booking("PENDING_PAYMENT", NOW.plusSeconds(60)));
        when(mapper.confirmSeatSold(anyLong(), any(), any(), any()))
                .thenReturn(1);
        when(mapper.insertPayment(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);
        when(mapper.confirmBookingPaid(
                anyLong(), any(), any(), any(), anyLong()
        )).thenReturn(1);

        ConfirmPaymentResult result = transaction.confirmOnce(command);

        assertThat(result.outcome())
                .isEqualTo(PaymentConfirmationOutcome.CONFIRMED);
        assertThat(result.paymentNumber()).isEqualTo(PAYMENT_NO);
        verify(mapper).confirmSeatSold(
                eq(99L), eq("A01"), eq(BOOKING_NO), any()
        );
        verify(mapper).confirmBookingPaid(
                eq(7L), eq(PAYMENT_NO), any(), any(), eq(3L)
        );
        verify(mapper, never()).insertRefundOutbox(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void recordsRefundOutboxWhenPaymentArrivesAfterDeadline() {
        ConfirmPaymentCommand command = command(NOW);
        when(mapper.selectBookingForUpdate(BOOKING_NO))
                .thenReturn(booking("PENDING_PAYMENT", NOW));
        when(mapper.insertPayment(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(1);
        when(mapper.insertRefundOutbox(any(), any(), any(), any(), any()))
                .thenReturn(1);

        ConfirmPaymentResult result = transaction.confirmOnce(command);

        assertThat(result.outcome())
                .isEqualTo(PaymentConfirmationOutcome.REFUND_PENDING);
        verify(mapper, never()).confirmSeatSold(
                anyLong(), any(), any(), any()
        );
        verify(mapper).insertRefundOutbox(
                any(), eq(PAYMENT_NO),
                org.mockito.ArgumentMatchers.contains("PAYMENT_WINDOW_EXPIRED"),
                any(), any()
        );
    }

    @Test
    void rejectsConflictingIdempotencyReplay() {
        ConfirmPaymentCommand command = command(NOW.minusSeconds(10));
        PaymentRecordRow existing = new PaymentRecordRow();
        existing.setId(1L);
        existing.setRequestNo(command.requestNumber());
        existing.setPaymentNo(UUID.randomUUID().toString());
        existing.setOrderNo(command.bookingNumber());
        existing.setAmount(command.amount());
        existing.setStatus("SUCCEEDED");
        existing.setCompletedAt(LocalDateTime.ofInstant(
                command.paidAt(), ZoneOffset.UTC
        ));
        when(mapper.selectByRequestNo(command.requestNumber()))
                .thenReturn(existing);

        assertThatThrownBy(() -> transaction.confirmOnce(command))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("conflicts");
    }

    private ConfirmPaymentCommand command(Instant paidAt) {
        return new ConfirmPaymentCommand(
                "callback-request-1",
                PAYMENT_NO,
                BOOKING_NO,
                new BigDecimal("12.50"),
                paidAt
        );
    }

    private BookingPaymentRow booking(String status, Instant expiresAt) {
        BookingPaymentRow row = new BookingPaymentRow();
        row.setId(7L);
        row.setOrderNo(BOOKING_NO);
        row.setTripId(99L);
        row.setSeatNumber("A01");
        row.setPriceSnapshot(new BigDecimal("12.50"));
        row.setStatus(status);
        row.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        row.setVersion(3L);
        return row;
    }
}
