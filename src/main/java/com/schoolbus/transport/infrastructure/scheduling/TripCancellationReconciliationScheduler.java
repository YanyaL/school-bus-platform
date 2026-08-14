package com.schoolbus.transport.infrastructure.scheduling;

import com.schoolbus.transport.application.trip.TripCancellationReconciliationResult;
import com.schoolbus.transport.application.trip.TripCancellationReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Profile("!test")
@ConditionalOnProperty(
        prefix = "school-bus.transport.trip-cancellation.reconciliation",
        name = "enabled",
        havingValue = "true"
)
public class TripCancellationReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            TripCancellationReconciliationScheduler.class
    );

    private final TripCancellationReconciliationService service;

    public TripCancellationReconciliationScheduler(
            TripCancellationReconciliationService service
    ) {
        this.service = Objects.requireNonNull(service);
    }

    @Scheduled(
            initialDelayString =
                    "${school-bus.transport.trip-cancellation.reconciliation.initial-delay-ms:30000}",
            fixedDelayString =
                    "${school-bus.transport.trip-cancellation.reconciliation.fixed-delay-ms:30000}"
    )
    public void reconcile() {
        try {
            TripCancellationReconciliationResult result = service.reconcile();
            if (result.scanned() > 0) {
                log.info(
                        "Trip cancellation reconciliation completed: scanned={}, finalized={}, alreadyFinalized={}",
                        result.scanned(),
                        result.finalized(),
                        result.alreadyFinalized()
                );
            }
        } catch (RuntimeException exception) {
            log.error("Trip cancellation reconciliation failed", exception);
        }
    }
}
