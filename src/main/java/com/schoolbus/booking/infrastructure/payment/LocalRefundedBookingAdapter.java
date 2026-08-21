package com.schoolbus.booking.infrastructure.payment;

import com.schoolbus.booking.config.ConditionalOnEmbeddedBooking;

import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingsSettledEvent;
import com.schoolbus.booking.application.tripcancellation.TripCancellationProgressPort;
import com.schoolbus.booking.application.tripcancellation.TripCancellationSettlementOutboxPort;
import com.schoolbus.payment.application.refund.RefundedBookingPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@ConditionalOnEmbeddedBooking
@Component
@Profile("!test")
public class LocalRefundedBookingAdapter implements RefundedBookingPort {

    private final BookingOrderRepository orderRepository;
    private final TripCancellationProgressPort progressPort;
    private final TripCancellationSettlementOutboxPort settlementOutboxPort;

    public LocalRefundedBookingAdapter(
            BookingOrderRepository orderRepository,
            TripCancellationProgressPort progressPort,
            TripCancellationSettlementOutboxPort settlementOutboxPort
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.progressPort = Objects.requireNonNull(progressPort);
        this.settlementOutboxPort = Objects.requireNonNull(settlementOutboxPort);
    }

    @Override
    public void markRefunded(
            BookingNumber bookingNumber,
            Instant refundedAt
    ) {
        BookingNumber checkedNumber = Objects.requireNonNull(bookingNumber);
        BookingOrder order = orderRepository
                .findByBookingNumber(checkedNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "booking was not found for refund " + checkedNumber
                ));
        if (order.status() == BookingStatus.REFUNDED
                || order.status() == BookingStatus.CANCELLED) {
            return;
        }
        if (order.status() != BookingStatus.REFUND_PENDING) {
            throw new IllegalStateException(
                    "booking is not waiting for refund: " + checkedNumber
            );
        }
        order.confirmRefund(
                Objects.requireNonNull(refundedAt, "refundedAt must not be null")
        );
        orderRepository.save(order);
        if (progressPort.completeRefund(
                order.tripReference().value(),
                refundedAt
        )) {
            settlementOutboxPort.append(
                    new TripCancellationBookingsSettledEvent(
                            order.tripReference().value(),
                            refundedAt
                    )
            );
        }
    }
}
