package com.schoolbus.payment.infrastructure.persistence;

import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.payment.domain.PaymentId;
import com.schoolbus.payment.domain.PaymentNumber;
import com.schoolbus.payment.domain.PaymentRecord;
import com.schoolbus.payment.domain.PaymentRequestNumber;
import com.schoolbus.payment.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyBatisPaymentRecordRepositoryTest {

    private static final Instant PAID_AT = Instant.parse("2026-08-08T00:10:00Z");
    private static final Instant RECORDED_AT = Instant.parse("2026-08-08T00:10:05Z");
    private static final String PAYMENT_NO = "77777777-7777-7777-7777-777777777777";
    private static final String BOOKING_NO = "55555555-5555-5555-5555-555555555555";

    @Mock
    private PaymentRecordMapper mapper;

    private MyBatisPaymentRecordRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MyBatisPaymentRecordRepository(mapper);
    }

    @Test
    void shouldInsertSucceededPayment() {
        when(mapper.insertPayment(any())).thenReturn(1);

        repository.save(payment());

        ArgumentCaptor<PaymentRecordDataObject> captor =
                ArgumentCaptor.forClass(PaymentRecordDataObject.class);
        verify(mapper).insertPayment(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9001L);
        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(captor.getValue().getAmount())
                .isEqualByComparingTo("5.50");
    }

    @Test
    void shouldRestorePaymentByRequestNumber() {
        when(mapper.selectByRequestNo("callback-1"))
                .thenReturn(dataObject());

        PaymentRecord restored = repository.findByRequestNumber(
                PaymentRequestNumber.of("callback-1")
        ).orElseThrow();

        assertThat(restored.paymentNumber().toString())
                .isEqualTo(PAYMENT_NO);
        assertThat(restored.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(restored.completedAt()).isEqualTo(PAID_AT);
    }

    private PaymentRecord payment() {
        return PaymentRecord.succeeded(
                PaymentId.of(9001L),
                PaymentNumber.of(PAYMENT_NO),
                PaymentRequestNumber.of("callback-1"),
                BookingNumber.of(BOOKING_NO),
                BookingAmount.of("5.50"),
                PAID_AT,
                RECORDED_AT
        );
    }

    private PaymentRecordDataObject dataObject() {
        PaymentRecordDataObject dataObject = new PaymentRecordDataObject();
        dataObject.setId(9001L);
        dataObject.setPaymentNo(PAYMENT_NO);
        dataObject.setRequestNo("callback-1");
        dataObject.setOrderNo(BOOKING_NO);
        dataObject.setAmount(new BigDecimal("5.50"));
        dataObject.setStatus("SUCCEEDED");
        dataObject.setCompletedAt(toDatabaseTime(PAID_AT));
        dataObject.setVersion(0L);
        dataObject.setCreatedAt(toDatabaseTime(RECORDED_AT));
        dataObject.setUpdatedAt(toDatabaseTime(RECORDED_AT));
        return dataObject;
    }

    private LocalDateTime toDatabaseTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
