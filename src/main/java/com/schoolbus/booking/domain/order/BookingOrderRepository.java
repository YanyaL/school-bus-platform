package com.schoolbus.booking.domain.order;

import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

public interface BookingOrderRepository {

    BookingOrder save(BookingOrder bookingOrder);

    Optional<BookingOrder> findById(BookingId bookingId);

    Optional<BookingOrder> findByBookingNumber(
            BookingNumber bookingNumber
    );

    Optional<BookingOrder> findByRequestNumber(
            BookingRequestNumber requestNumber
    );

    boolean existsActiveByUserIdAndTripReference(
            UserId userId,
            TripReference tripReference
    );

    boolean existsActiveByTripReference(
            TripReference tripReference
    );

    List<BookingOrder> findExpiredPendingOrders(
            Instant expiredAt,
            int limit
    );

    List<BookingOrder> findByUserId(
            UserId userId,
            BookingStatus status,
            int offset,
            int limit,
            boolean sortByCreatedAtAscending
    );

    long countByUserId(
            UserId userId,
            BookingStatus status
    );
}
