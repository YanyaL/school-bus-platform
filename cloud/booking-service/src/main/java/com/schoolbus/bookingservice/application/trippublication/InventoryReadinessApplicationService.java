package com.schoolbus.bookingservice.application.trippublication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class InventoryReadinessApplicationService {
    private static final Logger log = LoggerFactory.getLogger(
            InventoryReadinessApplicationService.class
    );
    private final InventoryReadinessStore store;
    private final InventoryReadinessTransaction transaction;
    private final int batchSize;

    public InventoryReadinessApplicationService(
            InventoryReadinessStore store,
            InventoryReadinessTransaction transaction,
            int batchSize
    ) {
        this.store = Objects.requireNonNull(store);
        this.transaction = Objects.requireNonNull(transaction);
        if (batchSize <= 0 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize must be in [1, 1000]");
        }
        this.batchSize = batchSize;
    }

    public InventoryReadinessResult verifyPending() {
        var candidates = store.findCandidates(batchSize);
        int ready = 0;
        int waiting = 0;
        int failed = 0;
        for (InventoryReadinessCandidate candidate : candidates) {
            try {
                var result = transaction.verify(candidate);
                if (result.status()
                        == InventoryReadinessObservation.Status.READY) {
                    ready++;
                } else {
                    waiting++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn(
                        "Inventory readiness verification failed: tripId={}, publicationVersion={}",
                        candidate.tripId(),
                        candidate.publicationVersion(),
                        exception
                );
            }
        }
        return new InventoryReadinessResult(
                candidates.size(),
                ready,
                waiting,
                failed
        );
    }
}
