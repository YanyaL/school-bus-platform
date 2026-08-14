package com.schoolbus.transport.infrastructure.booking;

import com.schoolbus.booking.domain.order.BookingOrderRepository;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.transport.application.trip.TripBookingStatePort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Profile("!test")
public class LocalTripBookingStateAdapter
        implements TripBookingStatePort {

    private final BookingOrderRepository bookingOrderRepository;

    public LocalTripBookingStateAdapter(
            BookingOrderRepository bookingOrderRepository
    ) {
        this.bookingOrderRepository = Objects.requireNonNull(
                bookingOrderRepository,
                "bookingOrderRepository must not be null"
        );
    }

    @Override
    public boolean hasActiveBookings(long tripId) {
        return bookingOrderRepository.existsActiveByTripReference(
                TripReference.of(tripId)
        );
    }
}
