package com.schoolbus.transport.infrastructure.booking;

import com.schoolbus.booking.domain.inventory.SeatInventory;
import com.schoolbus.booking.domain.inventory.SeatInventoryRepository;
import com.schoolbus.booking.domain.trip.TripReference;
import com.schoolbus.transport.application.trip.TripInventoryInitializationPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
@Profile("!test")
public class LocalTripInventoryInitializer
        implements TripInventoryInitializationPort {

    private final SeatInventoryRepository seatInventoryRepository;

    public LocalTripInventoryInitializer(
            SeatInventoryRepository seatInventoryRepository
    ) {
        this.seatInventoryRepository = Objects.requireNonNull(
                seatInventoryRepository,
                "seatInventoryRepository must not be null"
        );
    }

    @Override
    public void initialize(
            long tripId,
            int totalSeats,
            Instant initializedAt
    ) {
        SeatInventory inventory = SeatInventory.initialize(
                TripReference.of(tripId),
                totalSeats,
                Objects.requireNonNull(
                        initializedAt,
                        "initializedAt must not be null"
                )
        );
        seatInventoryRepository.save(inventory);
    }
}
