package com.schoolbus.bookingservice.application.trippublication;

import java.util.List;

public interface InventoryReadinessStore {
    List<InventoryReadinessCandidate> findCandidates(int limit);

    Integer findInventoryTotal(long tripId);

    List<String> findSeatNumbers(long tripId);

    void saveObservation(InventoryReadinessObservation observation);
}
