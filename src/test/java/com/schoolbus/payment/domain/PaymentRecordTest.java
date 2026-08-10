package com.schoolbus.payment.domain;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingNumber;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRecordTest {

    private static final Instant PAID_AT =
            Instant.parse("2026-08-08T00:10:00Z");
    private static final Instant RECORDED_AT =
            Instant.parse("2026-08-08T00:10:05Z");

    @Test
    void shouldCreateSucceededPayment() {
        PaymentRecord payment = PaymentRecord.succeeded(
                PaymentId.of(1L),
                PaymentNumber.of("77777777-7777-7777-7777-777777777777"),
                PaymentRequestNumber.of("callback-1"),
                BookingNumber.of("55555555-5555-5555-5555-555555555555"),
                BookingAmount.of("5.50"),
                PAID_AT,
                RECORDED_AT
        );

        assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.failureReason()).isNull();
        assertThat(payment.completedAt()).isEqualTo(PAID_AT);
    }

    @Test
    void refundPendingMustHaveReason() {
        assertThatThrownBy(() -> PaymentRecord.refundPending(
                PaymentId.of(1L),
                PaymentNumber.of("77777777-7777-7777-7777-777777777777"),
                PaymentRequestNumber.of("callback-1"),
                BookingNumber.of("55555555-5555-5555-5555-555555555555"),
                BookingAmount.of("5.50"),
                " ",
                PAID_AT,
                RECORDED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCompletePendingRefund() {
        PaymentRecord payment = refundPending();
        Instant refundedAt = RECORDED_AT.plusSeconds(10);

        payment.confirmRefund("refund-001", refundedAt);

        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.refundReference()).isEqualTo("refund-001");
        assertThat(payment.refundedAt()).isEqualTo(refundedAt);
        assertThat(payment.version()).isEqualTo(1L);
        assertThat(payment.updatedAt()).isEqualTo(refundedAt);
    }

    @Test
    void shouldNotRefundSucceededPayment() {
        PaymentRecord payment = PaymentRecord.succeeded(
                PaymentId.of(1L),
                PaymentNumber.of("77777777-7777-7777-7777-777777777777"),
                PaymentRequestNumber.of("callback-1"),
                BookingNumber.of("55555555-5555-5555-5555-555555555555"),
                BookingAmount.of("5.50"),
                PAID_AT,
                RECORDED_AT
        );

        assertThatThrownBy(() -> payment.confirmRefund(
                "refund-001",
                RECORDED_AT.plusSeconds(1)
        )).isInstanceOf(IllegalStateException.class);
    }

    private PaymentRecord refundPending() {
        return PaymentRecord.refundPending(
                PaymentId.of(1L),
                PaymentNumber.of("77777777-7777-7777-7777-777777777777"),
                PaymentRequestNumber.of("callback-1"),
                BookingNumber.of("55555555-5555-5555-5555-555555555555"),
                BookingAmount.of("5.50"),
                "PAYMENT_WINDOW_EXPIRED",
                PAID_AT,
                RECORDED_AT
        );
    }
}
