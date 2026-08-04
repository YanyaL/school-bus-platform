package com.schoolbus.transport.infrastructure.scheduling;

import com.schoolbus.transport.application.trip.TripStatusApplicationService;
import com.schoolbus.transport.application.trip.TripStatusUpdateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(
        prefix = "school-bus.trip-status.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class TripStatusScheduler {

    private static final Logger log = LoggerFactory.getLogger(
            TripStatusScheduler.class
    );

    private final TripStatusApplicationService applicationService;

    public TripStatusScheduler(
            TripStatusApplicationService applicationService
    ) {
        this.applicationService = Objects.requireNonNull(
                applicationService,
                "applicationService must not be null"
        );
    }

    @Scheduled(
            initialDelayString =
                    "${school-bus.trip-status.scheduler.initial-delay-ms:10000}",
            fixedDelayString =
                    "${school-bus.trip-status.scheduler.fixed-delay-ms:30000}"
    )
    public void updateDueTripStatuses() {
        try {
            TripStatusUpdateResult result = applicationService
                    .updateDueTripStatuses();
            log.info(
                    "Trip status update completed: "
                            + "closedBookings={}, departedTrips={}, "
                            + "optimisticLockConflicts={}",
                    result.closedBookings(),
                    result.departedTrips(),
                    result.optimisticLockConflicts()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Trip status scheduled update failed",
                    exception
            );
        }
    }
}
