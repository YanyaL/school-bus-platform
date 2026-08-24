package com.schoolbus.bookingservice.infrastructure.payment;

import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationRefundPort;
import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.support.payment.application.PaymentRefundOutboxPort;
import com.schoolbus.bookingservice.support.payment.application.RefundRequiredEvent;
import com.schoolbus.bookingservice.support.payment.domain.PaymentNumber;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * Booking-owned refund request: appends {@code RefundRequested} outbox only.
 * Payment owns SUCCEEDED→REFUND_PENDING via {@code PaymentRefundTransaction.prepareRefund}.
 */
@Component
@Profile("!test")
public class OutboxTripCancellationRefundAdapter
        implements TripCancellationRefundPort {

    public static final String REASON = "TRIP_CANCELLED";

    private final PaymentRefundOutboxPort refundOutboxPort;

    public OutboxTripCancellationRefundAdapter(
            PaymentRefundOutboxPort refundOutboxPort
    ) {
        this.refundOutboxPort = Objects.requireNonNull(refundOutboxPort);
    }

    @Override
    public void requestRefund(BookingOrder order, Instant requestedAt) {
        BookingOrder checkedOrder = Objects.requireNonNull(
                order,
                "order must not be null"
        );
        if (checkedOrder.paymentReference() == null
                || checkedOrder.paidAt() == null) {
            throw new IllegalStateException(
                    "paid booking does not contain a payment reference"
            );
        }
        Instant checkedTime = Objects.requireNonNull(requestedAt);
        refundOutboxPort.append(new RefundRequiredEvent(
                PaymentNumber.of(checkedOrder.paymentReference().toString()),
                checkedOrder.bookingNumber(),
                checkedOrder.amount(),
                REASON,
                checkedOrder.paidAt(),
                checkedTime
        ));
    }
}
