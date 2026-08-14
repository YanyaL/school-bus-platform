package com.schoolbus.transport.application.trip;

import com.schoolbus.shared.api.BusinessException;
import com.schoolbus.shared.api.ErrorCode;
import com.schoolbus.transport.domain.trip.BusTrip;
import com.schoolbus.transport.domain.trip.BusTripRepository;
import com.schoolbus.transport.domain.trip.TripId;
import com.schoolbus.transport.domain.trip.TripStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
@Profile("!test")
public class TripCancellationApplicationService {

    private final BusTripRepository tripRepository;
    private final TripBookingStatePort bookingStatePort;
    private final TripCancellationOutboxPort cancellationOutboxPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TripCancellationApplicationService(
            BusTripRepository tripRepository,
            TripBookingStatePort bookingStatePort,
            TripCancellationOutboxPort cancellationOutboxPort,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.tripRepository = Objects.requireNonNull(
                tripRepository,
                "tripRepository must not be null"
        );
        this.bookingStatePort = Objects.requireNonNull(
                bookingStatePort,
                "bookingStatePort must not be null"
        );
        this.cancellationOutboxPort = Objects.requireNonNull(
                cancellationOutboxPort,
                "cancellationOutboxPort must not be null"
        );
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher,
                "eventPublisher must not be null"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock must not be null"
        );
    }

    @Transactional
    public AdminTripView cancel(CancelTripCommand command) {
        CancelTripCommand validated = Objects.requireNonNull(
                command,
                "command must not be null"
        );
        if (validated.expectedVersion() < 0) {
            throw new IllegalArgumentException(
                    "expectedVersion must not be negative"
            );
        }

        BusTrip trip = tripRepository
                .findByIdForUpdate(TripId.of(validated.tripId()))
                .orElseThrow(() -> new TripNotFoundException(
                        validated.tripId()
                ));

        if (trip.status() == TripStatus.CANCELLED) {
            return AdminTripView.from(trip);
        }
        if (trip.status() == TripStatus.CANCELLATION_PENDING) {
            return AdminTripView.from(trip);
        }
        if (trip.version() != validated.expectedVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (trip.status() == TripStatus.DEPARTED
                || trip.status() == TripStatus.COMPLETED) {
            throw new TripNotCancellableException(
                    trip.tripId().value(),
                    trip.status()
            );
        }
        Instant now = clock.instant();
        boolean hasActiveBookings = trip.status() != TripStatus.DRAFT
                && bookingStatePort.hasActiveBookings(
                        trip.tripId().value()
                );
        if (hasActiveBookings) {
            trip.requestCancellation(now);
        } else {
            trip.cancel(now);
        }
        try {
            tripRepository.save(trip);
        } catch (OptimisticLockingFailureException exception) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        if (hasActiveBookings) {
            cancellationOutboxPort.append(
                    new TripCancellationRequestedEvent(
                            trip.tripId().value(),
                            trip.version(),
                            now
                    )
            );
        }

        eventPublisher.publishEvent(
                new TripAvailabilityChangedEvent(now)
        );
        return AdminTripView.from(trip);
    }
}
