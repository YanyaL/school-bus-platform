package com.schoolbus.booking.domain.order;

import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.shared.domain.identity.UserId;

import java.util.Optional;

public interface BookingOrderRepository {

    BookingOrder save(BookingOrder bookingOrder);

    Optional<BookingOrder> findById(BookingId bookingId);

    boolean existsActiveByUserIdAndTripReference(
            UserId userId,
            TripReference tripReference
    );
}
