package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.application.messaging.ConsumedEventStore;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
@Profile("!test")
public class TripCancellationCompletionTransaction {

    public static final String CONSUMER_NAME =
            "transport-trip-cancellation-settled-consumer";

    private final BusTripRepository tripRepository;
    private final ConsumedEventStore consumedEventStore;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TripCancellationCompletionTransaction(
            BusTripRepository tripRepository,
            ConsumedEventStore consumedEventStore,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.tripRepository = Objects.requireNonNull(tripRepository);
        this.consumedEventStore = Objects.requireNonNull(consumedEventStore);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(TripCancellationSettledEnvelope envelope) {
        TripCancellationSettledEnvelope checked = Objects.requireNonNull(
                envelope,
                "envelope must not be null"
        );
        Instant now = clock.instant();
        if (!consumedEventStore.insertIfAbsent(
                CONSUMER_NAME,
                checked.eventId(),
                now
        )) {
            return false;
        }

        BusTrip trip = tripRepository.findByIdForUpdate(
                        TripId.of(checked.payload().tripId())
                )
                .orElseThrow(() -> new TripNotFoundException(
                        checked.payload().tripId()
                ));
        if (trip.status() == TripStatus.CANCELLED) {
            return false;
        }
        if (trip.status() != TripStatus.CANCELLATION_PENDING) {
            throw new TripNotCancellableException(
                    trip.tripId().value(),
                    trip.status()
            );
        }

        trip.completeCancellation(now);
        tripRepository.save(trip);
        eventPublisher.publishEvent(new TripAvailabilityChangedEvent(now));
        return true;
    }
}
