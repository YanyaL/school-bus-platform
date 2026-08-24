package com.schoolbus.bookingservice.application.payment;

import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationBookingsSettledEvent;
import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationProgressPort;
import com.schoolbus.bookingservice.application.tripcancellation.TripCancellationSettlementOutboxPort;
import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.domain.order.BookingOrderRepository;
import com.schoolbus.bookingservice.domain.order.BookingStatus;
import com.schoolbus.bookingservice.domain.order.CancellationReason;
import com.schoolbus.bookingservice.shared.application.messaging.ConsumedEventStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
@Profile("!test")
public class PaymentRefundedBookingTransaction {

    public static final String CONSUMER_NAME =
            "booking-payment-refunded-consumer";
    static final String TRIP_CANCELLED = "TRIP_CANCELLED";

    private final BookingOrderRepository orderRepository;
    private final ConsumedEventStore consumedEventStore;
    private final TripCancellationProgressPort progressPort;
    private final TripCancellationSettlementOutboxPort settlementOutboxPort;
    private final Clock clock;

    public PaymentRefundedBookingTransaction(
            BookingOrderRepository orderRepository,
            ConsumedEventStore consumedEventStore,
            TripCancellationProgressPort progressPort,
            TripCancellationSettlementOutboxPort settlementOutboxPort,
            Clock clock
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.consumedEventStore = Objects.requireNonNull(consumedEventStore);
        this.progressPort = Objects.requireNonNull(progressPort);
        this.settlementOutboxPort = Objects.requireNonNull(
                settlementOutboxPort
        );
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRefundedResult process(PaymentRefundedEnvelope envelope) {
        PaymentRefundedEnvelope checked = Objects.requireNonNull(
                envelope,
                "envelope must not be null"
        );
        PaymentRefundedMessage message = checked.payload();
        Instant now = clock.instant();
        if (!consumedEventStore.insertIfAbsent(
                CONSUMER_NAME,
                checked.eventId(),
                now
        )) {
            return result(message, PaymentRefundedOutcome.DUPLICATE);
        }

        BookingOrder order = orderRepository.findByBookingNumber(
                BookingNumber.of(message.bookingNumber())
        ).orElse(null);
        if (order == null) {
            return result(message, PaymentRefundedOutcome.IGNORED);
        }
        if (order.status() == BookingStatus.REFUNDED) {
            return result(message, PaymentRefundedOutcome.ALREADY_REFUNDED);
        }
        if (order.status() != BookingStatus.REFUND_PENDING) {
            return result(message, PaymentRefundedOutcome.IGNORED);
        }
        validateMessageMatchesOrder(order, message);

        Instant refundedAt = latest(message.refundedAt(), order.updatedAt());
        order.confirmRefund(refundedAt);
        orderRepository.save(order);

        if (order.cancellationReason() == CancellationReason.TRIP_CANCELLED) {
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
        return result(message, PaymentRefundedOutcome.APPLIED);
    }

    private void validateMessageMatchesOrder(
            BookingOrder order,
            PaymentRefundedMessage message
    ) {
        if (!message.paymentNumber().equals(
                order.paymentReference().toString()
        )) {
            throw new PaymentRefundedMessageConflictException(
                    "paymentNumber does not match the booking payment"
            );
        }
        if (!message.reason().equals(order.cancellationReason().name())) {
            throw new PaymentRefundedMessageConflictException(
                    "refund reason does not match the booking cancellation"
            );
        }
    }

    private PaymentRefundedResult result(
            PaymentRefundedMessage message,
            PaymentRefundedOutcome outcome
    ) {
        return new PaymentRefundedResult(
                message.paymentNumber(),
                message.bookingNumber(),
                outcome
        );
    }

    private Instant latest(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }
}
