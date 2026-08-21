package com.schoolbus.bookingservice.domain.order;

import com.schoolbus.bookingservice.domain.trip.TripReference;
import com.schoolbus.bookingservice.shared.domain.identity.UserId;

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

    List<BookingOrder> findActiveByTripReferenceForUpdate(
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
