package com.schoolbus.bookingservice.application.booking;

import com.schoolbus.bookingservice.domain.inventory.SeatInventory;
import com.schoolbus.bookingservice.domain.inventory.SeatInventoryRepository;
import com.schoolbus.bookingservice.domain.order.BookingId;
import com.schoolbus.bookingservice.domain.order.BookingOrder;
import com.schoolbus.bookingservice.domain.order.BookingOrderRepository;
import com.schoolbus.bookingservice.domain.order.BookingNumber;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@Profile("!test")
public class BookingExpirationTransaction {

    private final BookingOrderRepository bookingOrderRepository;
    private final SeatInventoryRepository seatInventoryRepository;
    private final TripSeatReservationPort tripSeatReservationPort;

    public BookingExpirationTransaction(
            BookingOrderRepository bookingOrderRepository,
            SeatInventoryRepository seatInventoryRepository,
            TripSeatReservationPort tripSeatReservationPort
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
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireOne(
            BookingId bookingId,
            Instant expiredAt
    ) {
        return expireOne(bookingId, null, expiredAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireOne(
            BookingId bookingId,
            BookingNumber expectedBookingNumber,
            Instant expiredAt
    ) {
        BookingId validatedId = Objects.requireNonNull(
                bookingId,
                "bookingId must not be null"
        );
        Instant operationTime = Objects.requireNonNull(
                expiredAt,
                "expiredAt must not be null"
        );
        BookingOrder order = bookingOrderRepository
                .findById(validatedId)
                .orElse(null);
        if (order == null) {
            return false;
        }
        if (expectedBookingNumber != null
                && !expectedBookingNumber.equals(order.bookingNumber())) {
            throw new BookingExpirationMessageConflictException(
                    validatedId.value()
            );
        }
        if (!order.isPaymentExpiredAt(operationTime)) {
            return false;
        }

        SeatInventory inventory = seatInventoryRepository
                .findByTripReference(order.tripReference())
                .orElseThrow(
                        () -> new SeatInventoryNotFoundException(
                                order.tripReference()
                        )
                );
        order.expire(operationTime);

        boolean released = tripSeatReservationPort.releaseSeat(
                new SeatReleaseRequest(
                        order.tripReference(),
                        order.seatNumber(),
                        order.bookingNumber(),
                        operationTime
                )
        );
        if (!released) {
            throw new OptimisticLockingFailureException(
                    "seat lock was already released or changed"
            );
        }

        inventory.release(operationTime);
        seatInventoryRepository.save(inventory);
        bookingOrderRepository.save(order);
        return true;
    }
}
