package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.config.ConditionalOnEmbeddedBooking;

import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingStatus;
import com.schoolbus.payment.application.PaymentRefundOutboxPort;
import com.schoolbus.payment.application.RefundRequiredEvent;
import com.schoolbus.payment.domain.PaymentNumber;
import com.schoolbus.shared.domain.identity.UserId;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@ConditionalOnEmbeddedBooking
@Service
@Profile("!test")
public class BookingCancellationTransaction {

    public static final String USER_CANCELLED_REASON = "USER_CANCELLED";

    private final BookingOrderRepository bookingOrderRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final TripSeatReservationPort tripSeatReservationPort;
    private final PaymentRefundOutboxPort refundOutboxPort;
    private final Clock clock;

    public BookingCancellationTransaction(
            BookingOrderRepository bookingOrderRepository,
            SeatInventoryRepository seatInventoryRepository,
            TripSeatReservationPort tripSeatReservationPort,
            PaymentRefundOutboxPort refundOutboxPort,
            Clock clock
    ) {
        this.bookingOrderRepository = Objects.requireNonNull(
                bookingOrderRepository,
                "bookingOrderRepository must not be null"
        );
        this.seatInventoryRepository = Objects.requireNonNull(
                seatInventoryRepository,
                "seatInventoryRepository must not be null"
        );
        this.tripSeatReservationPort = Objects.requireNonNull(
                tripSeatReservationPort,
                "tripSeatReservationPort must not be null"
        );
        this.refundOutboxPort = Objects.requireNonNull(
                refundOutboxPort,
                "refundOutboxPort must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CancelBookingResult cancelOne(
            UserId userId,
            BookingNumber bookingNumber
    ) {
        UserId validatedUserId = Objects.requireNonNull(
                userId,
                "userId must not be null"
        );
        BookingNumber validatedNumber = Objects.requireNonNull(
                bookingNumber,
                "bookingNumber must not be null"
        );
        BookingOrder order = bookingOrderRepository
                .findByBookingNumber(validatedNumber)
                .orElseThrow(
                        () -> new BookingNotFoundException(validatedNumber)
                );
        if (!order.userId().equals(validatedUserId)) {
            throw new BookingNotFoundException(validatedNumber);
        }
        if (order.status() == BookingStatus.CANCELLED
                || order.status() == BookingStatus.REFUND_PENDING
                || order.status() == BookingStatus.REFUNDED) {
            return CancelBookingResult.from(order, false);
        }
        if (order.status() == BookingStatus.PENDING_PAYMENT) {
            return cancelPendingPayment(order);
        }
        if (order.status() == BookingStatus.PAID) {
            return cancelPaid(order);
        }
        throw new BookingNotCancellableException(
                validatedNumber,
                order.status()
        );
    }

    private CancelBookingResult cancelPendingPayment(BookingOrder order) {
        Instant cancelledAt = clock.instant();
        SeatInventory inventory = requireInventory(order);
        order.cancel(cancelledAt);
        ensureReleased(tripSeatReservationPort.releaseSeat(
                new SeatReleaseRequest(
                        order.tripReference(),
                        order.seatNumber(),
                        order.bookingNumber(),
                        cancelledAt
                )
        ));
        inventory.release(cancelledAt);
        seatInventoryRepository.save(inventory);
        bookingOrderRepository.save(order);
        return CancelBookingResult.from(order, true);
    }

    private CancelBookingResult cancelPaid(BookingOrder order) {
        Instant cancelledAt = clock.instant();
        SeatInventory inventory = requireInventory(order);
        order.requestRefundBecauseUserCancelled(cancelledAt);
        ensureReleased(tripSeatReservationPort.releaseSoldSeat(
                new SeatReleaseRequest(
                        order.tripReference(),
                        order.seatNumber(),
                        order.bookingNumber(),
                        cancelledAt
                )
        ));
        inventory.release(cancelledAt);
        seatInventoryRepository.save(inventory);
        if (order.paymentReference() == null || order.paidAt() == null) {
            throw new IllegalStateException(
                    "paid booking does not contain a payment reference"
            );
        }
        refundOutboxPort.append(new RefundRequiredEvent(
                PaymentNumber.of(order.paymentReference().toString()),
                order.bookingNumber(),
                order.amount(),
                USER_CANCELLED_REASON,
                order.paidAt(),
                cancelledAt
        ));
        bookingOrderRepository.save(order);
        return CancelBookingResult.from(order, true);
    }

    private SeatInventory requireInventory(BookingOrder order) {
        return seatInventoryRepository
                .findByTripReference(order.tripReference())
                .orElseThrow(
                        () -> new SeatInventoryNotFoundException(
                                order.tripReference()
                        )
                );
    }

    private void ensureReleased(boolean released) {
        if (!released) {
            throw new OptimisticLockingFailureException(
                    "seat lock was already released or changed"
            );
        }
    }
}
