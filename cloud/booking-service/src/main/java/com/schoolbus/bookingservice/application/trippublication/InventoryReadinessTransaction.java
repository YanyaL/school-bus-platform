package com.schoolbus.bookingservice.application.trippublication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class InventoryReadinessTransaction {
    private final InventoryReadinessStore store;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InventoryReadinessTransaction(
            InventoryReadinessStore store,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.store = Objects.requireNonNull(store);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InventoryReadinessObservation verify(
            InventoryReadinessCandidate candidate
    ) {
        Objects.requireNonNull(candidate);
        Set<String> expectedSeats = expectedSeats(candidate.snapshotJson());
        Integer inventoryTotal = store.findInventoryTotal(candidate.tripId());
        List<String> observedSeatList = List.copyOf(
                store.findSeatNumbers(candidate.tripId())
        );
        Set<String> observedSeats = new HashSet<>(observedSeatList);

        String diagnostic = diagnostic(
                expectedSeats,
                inventoryTotal,
                observedSeatList,
                observedSeats
        );
        InventoryReadinessObservation observation =
                new InventoryReadinessObservation(
                        candidate.tripId(),
                        candidate.tripNumber(),
                        candidate.publicationVersion(),
                        expectedSeats.size(),
                        inventoryTotal,
                        observedSeatList.size(),
                        diagnostic == null
                                ? InventoryReadinessObservation.Status.READY
                                : InventoryReadinessObservation.Status.WAITING,
                        diagnostic,
                        clock.instant()
                );
        store.saveObservation(observation);
        return observation;
    }

    private Set<String> expectedSeats(String snapshotJson) {
        try {
            JsonNode seats = objectMapper.readTree(snapshotJson)
                    .required("seatNumbers");
            if (!seats.isArray() || seats.isEmpty()) {
                throw new IllegalArgumentException(
                        "publication snapshot has no seats"
                );
            }
            Set<String> result = new HashSet<>();
            for (JsonNode seat : seats) {
                if (!seat.isTextual() || seat.textValue().isBlank()
                        || !result.add(seat.textValue())) {
                    throw new IllegalArgumentException(
                            "publication snapshot contains invalid seats"
                    );
                }
            }
            return Set.copyOf(result);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "invalid publication snapshot JSON",
                    exception
            );
        }
    }

    private String diagnostic(
            Set<String> expectedSeats,
            Integer inventoryTotal,
            List<String> observedSeatList,
            Set<String> observedSeats
    ) {
        if (inventoryTotal == null) {
            return "INVENTORY_MISSING";
        }
        if (inventoryTotal != expectedSeats.size()) {
            return "INVENTORY_TOTAL_MISMATCH";
        }
        if (observedSeatList.size() != observedSeats.size()) {
            return "DUPLICATE_SEAT_ROWS";
        }
        if (!observedSeats.equals(expectedSeats)) {
            return "SEAT_SET_MISMATCH";
        }
        return null;
    }
}
