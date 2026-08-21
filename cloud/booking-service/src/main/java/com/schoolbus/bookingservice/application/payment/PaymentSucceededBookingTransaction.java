package com.schoolbus.bookingservice.application.payment;

import com.schoolbus.bookingservice.application.booking.SeatSaleRequest;
import com.schoolbus.bookingservice.application.booking.TripSeatReservationPort;
import com.schoolbus.bookingservice.domain.order.BookingAmount;
import com.schoolbus.bookingservice.domain.order.BookingNumber;
import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.domain.order.BookingOrderRepository;
import com.schoolbus.bookingservice.domain.order.BookingStatus;
import com.schoolbus.bookingservice.domain.order.PaymentReference;
import com.schoolbus.bookingservice.shared.application.messaging.ConsumedEventStore;
import com.schoolbus.bookingservice.support.payment.application.PaymentRefundOutboxPort;
import com.schoolbus.bookingservice.support.payment.application.RefundRequiredEvent;
import com.schoolbus.bookingservice.support.payment.domain.PaymentNumber;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
@Profile("!test")
public class PaymentSucceededBookingTransaction {

    public static final String CONSUMER_NAME =
            "booking-payment-succeeded-consumer";

    private final BookingOrderRepository orderRepository;
    private final TripSeatReservationPort seatReservationPort;
    private final ConsumedEventStore consumedEventStore;
    private final PaymentRefundOutboxPort refundOutbox;
    private final Clock clock;

    public PaymentSucceededBookingTransaction(
            BookingOrderRepository orderRepository,
            TripSeatReservationPort seatReservationPort,
            ConsumedEventStore consumedEventStore,
            PaymentRefundOutboxPort refundOutbox,
            Clock clock
    ) {
        this.orderRepository = Objects.requireNonNull(orderRepository);
        this.seatReservationPort = Objects.requireNonNull(seatReservationPort);
        this.consumedEventStore = Objects.requireNonNull(consumedEventStore);
        this.refundOutbox = Objects.requireNonNull(refundOutbox);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentSucceededResult process(
            PaymentSucceededEnvelope envelope
    ) {
        PaymentSucceededEnvelope checked = Objects.requireNonNull(
                envelope,
                "envelope must not be null"
        );
        PaymentSucceededMessage message = checked.payload();
        if (!consumedEventStore.insertIfAbsent(
                CONSUMER_NAME,
                checked.eventId(),
                clock.instant()
        )) {
            return result(message, PaymentSucceededOutcome.DUPLICATE);
        }

        BookingNumber bookingNumber = BookingNumber.of(
                message.bookingNumber()
        );
        BookingOrder order = orderRepository.findByBookingNumber(
                bookingNumber
        ).orElse(null);
        if (order == null) {
            return requestRefund(message, "BOOKING_NOT_FOUND");
        }
        if (!amountMatches(order, message)) {
            return requestRefund(message, "PAYMENT_AMOUNT_MISMATCH");
        }

        PaymentReference paymentReference = PaymentReference.of(
                message.paymentNumber()
        );
        if (order.status() == BookingStatus.PAID) {
            if (!paymentReference.equals(order.paymentReference())) {
                return requestRefund(
                        message,
                        "BOOKING_ALREADY_PAID"
                );
            }
            ensureSamePaymentTime(order, message);
            return result(
                    message,
                    PaymentSucceededOutcome.ALREADY_APPLIED
            );
        }
        if (order.status() != BookingStatus.PENDING_PAYMENT) {
            return requestRefund(message, "BOOKING_NOT_PAYABLE");
        }
        if (!message.paidAt().isBefore(order.expiresAt())) {
            return requestRefund(message, "PAYMENT_WINDOW_EXPIRED");
        }

        Instant confirmedAt = latest(
                message.occurredAt(),
                order.updatedAt()
        );
        boolean sold = seatReservationPort.confirmSeatSold(
                new SeatSaleRequest(
                        order.tripReference(),
                        order.seatNumber(),
                        order.bookingNumber(),
                        confirmedAt
                )
        );
        if (!sold) {
            return requestRefund(message, "SEAT_LOCK_LOST");
        }

        order.confirmPayment(
                paymentReference,
                message.paidAt(),
                confirmedAt
        );
        orderRepository.save(order);
        return result(message, PaymentSucceededOutcome.APPLIED);
    }

    private boolean amountMatches(
            BookingOrder order,
            PaymentSucceededMessage message
    ) {
        BookingAmount eventAmount = new BookingAmount(message.amount());
        return order.amount().equals(eventAmount);
    }

    private void ensureSamePaymentTime(
            BookingOrder order,
            PaymentSucceededMessage message
    ) {
        if (!message.paidAt().equals(order.paidAt())) {
            throw new PaymentSucceededMessageConflictException(
                    "payment event conflicts with the applied payment time"
            );
        }
    }

    private PaymentSucceededResult requestRefund(
            PaymentSucceededMessage message,
            String reason
    ) {
        refundOutbox.append(new RefundRequiredEvent(
                PaymentNumber.of(message.paymentNumber()),
                BookingNumber.of(message.bookingNumber()),
                new BookingAmount(message.amount()),
                reason,
                message.paidAt(),
                clock.instant()
        ));
        return result(message, PaymentSucceededOutcome.REFUND_REQUIRED);
    }

    private PaymentSucceededResult result(
            PaymentSucceededMessage message,
            PaymentSucceededOutcome outcome
    ) {
        return new PaymentSucceededResult(
                message.paymentNumber(),
                message.bookingNumber(),
                outcome
        );
    }

    private Instant latest(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }
}
