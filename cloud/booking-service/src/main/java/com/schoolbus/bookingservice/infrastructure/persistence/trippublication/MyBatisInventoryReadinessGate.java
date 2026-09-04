package com.schoolbus.bookingservice.infrastructure.persistence.trippublication;

import com.schoolbus.bookingservice.application.booking.InventoryReadinessGate;
import com.schoolbus.bookingservice.domain.trip.TripReference;

import java.util.Objects;

public final class MyBatisInventoryReadinessGate
        implements InventoryReadinessGate {

    private final InventoryReadinessMapper mapper;

    public MyBatisInventoryReadinessGate(InventoryReadinessMapper mapper) {
        this.mapper = Objects.requireNonNull(
                mapper,
                "mapper must not be null"
        );
    }

    @Override
    public boolean isReady(TripReference tripReference, long tripVersion) {
        TripReference checked = Objects.requireNonNull(
                tripReference,
                "tripReference must not be null"
        );
        if (tripVersion <= 0L) {
            throw new IllegalArgumentException(
                    "tripVersion must be positive"
            );
        }
        return mapper.isReadyForPublication(
                checked.value(),
                tripVersion
        );
    }
}
