package com.schoolbus.transport.application.trip;

import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Service
@Profile("!test")
public class TripStatusApplicationService {

    static final int BATCH_SIZE = 100;

    private final BusTripRepository busTripRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public TripStatusApplicationService(
            BusTripRepository busTripRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.busTripRepository = Objects.requireNonNull(
                busTripRepository,
                "busTripRepository must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "eventPublisher must not be null"
        );
    }

    @Transactional
    public TripStatusUpdateResult updateDueTripStatuses() {
        Instant now = clock.instant();

        UpdateCount closingCount = updateTrips(
                busTripRepository.findDueOpenTripsForClosing(
                        now,
                        BATCH_SIZE
                ),
                trip -> trip.closeBooking(now)
        );
        UpdateCount departureCount = updateTrips(
                busTripRepository.findDueClosedTripsForDeparture(
                        now,
                        BATCH_SIZE
                ),
                trip -> trip.depart(now)
        );

        TripStatusUpdateResult result = new TripStatusUpdateResult(
                closingCount.updated(),
                departureCount.updated(),
                closingCount.conflicts()
                        + departureCount.conflicts()
        );
        if (result.closedBookings() > 0
                || result.departedTrips() > 0) {
            eventPublisher.publishEvent(
                    new TripAvailabilityChangedEvent(now)
            );
        }
        return result;
    }

    private UpdateCount updateTrips(
            List<BusTrip> trips,
            Consumer<BusTrip> transition
    ) {
        int updated = 0;
        int conflicts = 0;
        for (BusTrip trip : trips) {
            transition.accept(trip);
            try {
                busTripRepository.save(trip);
                updated++;
            } catch (OptimisticLockingFailureException exception) {
                conflicts++;
            }
        }
        return new UpdateCount(updated, conflicts);
    }

    private record UpdateCount(int updated, int conflicts) {
    }
}
