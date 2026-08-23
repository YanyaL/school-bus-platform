package com.schoolbus.bookingservice.infrastructure.payment;

import com.schoolbus.bookingservice.domain.order.BookingAmount;
import com.schoolbus.bookingservice.domain.order.BookingId;
import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.domain.order.BookingRequestNumber;
import com.schoolbus.bookingservice.domain.order.PaymentReference;
import com.schoolbus.bookingservice.domain.order.SeatNumber;
import com.schoolbus.bookingservice.domain.trip.PublicTripNumber;
import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.domain.identity.UserId;
import com.schoolbus.bookingservice.support.payment.application.PaymentRefundOutboxPort;
import com.schoolbus.bookingservice.support.payment.application.RefundRequiredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OutboxTripCancellationRefundAdapterTest {

    private static final Instant NOW =
            Instant.parse("2026-08-21T10:00:00Z");
    private static final Instant PAID_AT =
            Instant.parse("2026-08-21T09:50:00Z");

    private PaymentRefundOutboxPort refundOutboxPort;
    private OutboxTripCancellationRefundAdapter adapter;

    @BeforeEach
    void setUp() {
        refundOutboxPort = mock(PaymentRefundOutboxPort.class);
        adapter = new OutboxTripCancellationRefundAdapter(refundOutboxPort);
    }

    @Test
    void shouldAppendExactlyOneRefundRequestedFromBookingOrder() {
        BookingOrder order = paidOrder();

        adapter.requestRefund(order, NOW);

        ArgumentCaptor<RefundRequiredEvent> captor =
                ArgumentCaptor.forClass(RefundRequiredEvent.class);
        verify(refundOutboxPort, times(1)).append(captor.capture());
        RefundRequiredEvent event = captor.getValue();
        assertThat(event.reason())
                .isEqualTo(OutboxTripCancellationRefundAdapter.REASON);
        assertThat(event.paymentNumber().toString())
                .isEqualTo(order.paymentReference().toString());
        assertThat(event.bookingNumber()).isEqualTo(order.bookingNumber());
        assertThat(event.amount()).isEqualTo(order.amount());
        assertThat(event.paidAt()).isEqualTo(PAID_AT);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    private BookingOrder paidOrder() {
        BookingOrder order = BookingOrder.place(
                BookingId.of(1L),
                BookingNumber.of("55555555-5555-5555-5555-555555555555"),
                BookingRequestNumber.of("request-1"),
                UserId.of(1001L),
                TripReference.of(2001L),
                PublicTripNumber.of(
                        "22222222-2222-2222-2222-222222222222"
                ),
                SeatNumber.of("A01"),
                BookingAmount.of("5.50"),
                NOW.plusSeconds(900),
                NOW.minusSeconds(600)
        );
        order.confirmPayment(
                PaymentReference.of(
                        "66666666-6666-6666-6666-666666666666"
                ),
                PAID_AT,
                PAID_AT.plusSeconds(1)
        );
        return order;
    }
}
