package com.schoolbus.booking.application.tripcancellation;

public record TripCancellationBookingResult(
        long tripId,
        int cancelledBookings,
        int refundsRequested,
        boolean duplicateEvent
) {
    public static TripCancellationBookingResult duplicate(long tripId) {
        return new TripCancellationBookingResult(tripId, 0, 0, true);
    }
}
