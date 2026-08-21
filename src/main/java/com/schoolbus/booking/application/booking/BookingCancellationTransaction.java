package com.schoolbus.booking.application.booking;

import com.schoolbus.booking.config.ConditionalOnEmbeddedBooking;

import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.order.BookingNumber;
import com.schoolbus.booking.domain.order.BookingOrder;
import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.order.BookingStatus;
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

    private final BookingOrderRepository bookingOrderRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final TripSeatReservationPort tripSeatReservationPort;
    private final Clock clock;

    public BookingCancellationTransaction(
            BookingOrderRepository bookingOrderRepository,
            SeatInventoryRepository seatInventoryRepository,
            TripSeatReservationPort tripSeatReservationPort,
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
        if (order.status() == BookingStatus.CANCELLED) {
            return CancelBookingResult.from(order, false);
        }
        if (order.status() != BookingStatus.PENDING_PAYMENT) {
            throw new BookingNotCancellableException(
                    validatedNumber,
                    order.status()
            );
        }

        Instant cancelledAt = clock.instant();
        SeatInventory inventory = seatInventoryRepository
                .findByTripReference(order.tripReference())
                .orElseThrow(
                        () -> new SeatInventoryNotFoundException(
                                order.tripReference()
                        )
                );
        order.cancel(cancelledAt);

        boolean released = tripSeatReservationPort.releaseSeat(
                new SeatReleaseRequest(
                        order.tripReference(),
                        order.seatNumber(),
                        order.bookingNumber(),
                        cancelledAt
                )
        );
        if (!released) {
            throw new OptimisticLockingFailureException(
                    "seat lock was already released or changed"
            );
        }

        inventory.release(cancelledAt);
        seatInventoryRepository.save(inventory);
        bookingOrderRepository.save(order);
        return CancelBookingResult.from(order, true);
    }
}
