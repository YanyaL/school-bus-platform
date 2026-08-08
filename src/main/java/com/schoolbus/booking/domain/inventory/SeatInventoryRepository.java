package com.schoolbus.booking.domain.inventory;

import com.schoolbus.booking.domain.trip.TripReference;

import java.util.Optional;

public interface SeatInventoryRepository {

    /**
     * Persists the aggregate. Infrastructure implementations must use
     * its version for a conditional update and reject stale writes.
     */
    SeatInventory save(SeatInventory seatInventory);

    Optional<SeatInventory> findByTripReference(
            TripReference tripReference
    );
}
