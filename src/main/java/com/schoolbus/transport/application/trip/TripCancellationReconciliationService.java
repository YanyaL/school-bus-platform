package com.schoolbus.transport.application.trip;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Profile("!test")
public class TripCancellationReconciliationService {

    private static final String EVENT_NAMESPACE =
            "trip-cancellation-reconciliation:";

    private final TripCancellationReconciliationPort reconciliationPort;
    private final TripCancellationCompletionTransaction completionTransaction;
    private final Clock clock;
    private final Duration gracePeriod;
    private final int batchSize;

    public TripCancellationReconciliationService(
            TripCancellationReconciliationPort reconciliationPort,
            TripCancellationCompletionTransaction completionTransaction,
            Clock clock,
            @Value("${school-bus.transport.trip-cancellation.reconciliation.grace-period:PT2M}")
            Duration gracePeriod,
            @Value("${school-bus.transport.trip-cancellation.reconciliation.batch-size:100}")
            int batchSize
    ) {
        this.reconciliationPort = Objects.requireNonNull(reconciliationPort);
        this.completionTransaction = Objects.requireNonNull(
                completionTransaction
        );
        this.clock = Objects.requireNonNull(clock);
        this.gracePeriod = requireNonNegative(gracePeriod);
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
    }

    public TripCancellationReconciliationResult reconcile() {
        Instant now = clock.instant();
        List<Long> tripIds = reconciliationPort
                .findSettledCancellationsAwaitingFinalization(
                        now.minus(gracePeriod),
                        batchSize
                );
        int finalized = 0;
        int alreadyFinalized = 0;
        for (Long tripId : tripIds) {
            TripCancellationSettledEnvelope envelope =
                    new TripCancellationSettledEnvelope(
                            reconciliationEventId(tripId),
                            new TripCancellationSettledMessage(tripId, now)
                    );
            if (completionTransaction.complete(envelope)) {
                finalized++;
            } else {
                alreadyFinalized++;
            }
        }
        return new TripCancellationReconciliationResult(
                tripIds.size(),
                finalized,
                alreadyFinalized
        );
    }

    private String reconciliationEventId(long tripId) {
        if (tripId <= 0L) {
            throw new IllegalArgumentException("tripId must be positive");
        }
        return UUID.nameUUIDFromBytes(
                (EVENT_NAMESPACE + tripId)
                        .getBytes(StandardCharsets.UTF_8)
        ).toString();
    }

    private Duration requireNonNegative(Duration value) {
        Duration checked = Objects.requireNonNull(
                value,
                "gracePeriod must not be null"
        );
        if (checked.isNegative()) {
            throw new IllegalArgumentException(
                    "gracePeriod must not be negative"
            );
        }
        return checked;
    }
}
