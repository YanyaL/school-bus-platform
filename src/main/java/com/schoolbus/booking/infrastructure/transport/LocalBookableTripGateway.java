package com.schoolbus.booking.infrastructure.transport;

import com.schoolbus.booking.application.booking.BookableTripGateway;
import com.schoolbus.booking.application.booking.BookableTripSnapshot;
import com.schoolbus.booking.domain.order.BookingAmount;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@Profile("!test")
public class LocalBookableTripGateway
        implements BookableTripGateway {

    private final BusTripRepository busTripRepository;

    public LocalBookableTripGateway(
            BusTripRepository busTripRepository
    ) {
        this.busTripRepository = Objects.requireNonNull(
                busTripRepository,
                "busTripRepository must not be null"
        );
    }

    @Override
    public Optional<BookableTripSnapshot> findByTripReference(
            TripReference tripReference
    ) {
        TripReference validatedReference = Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
        );
        return busTripRepository
                .findById(TripId.of(validatedReference.value()))
                .map(this::toSnapshot);
    }

    private BookableTripSnapshot toSnapshot(BusTrip trip) {
        return new BookableTripSnapshot(
                TripReference.of(trip.tripId().value()),
                new BookingAmount(trip.price().amount()),
                trip.departureTime(),
                trip.bookingDeadline(),
                trip.status() == TripStatus.OPEN_FOR_BOOKING
        );
    }
}
