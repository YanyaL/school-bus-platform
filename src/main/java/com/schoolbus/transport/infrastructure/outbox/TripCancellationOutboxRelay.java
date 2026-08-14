package com.schoolbus.transport.infrastructure.outbox;

import com.schoolbus.booking.application.tripcancellation.TripCancellationBookingsSettledEvent;
import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;
import com.schoolbus.payment.infrastructure.outbox.MyBatisOutboxRelayRepository;
import com.schoolbus.payment.infrastructure.outbox.OutboxRelayResult;
import com.schoolbus.transport.application.trip.TripCancellationRequestedEvent;
import com.schoolbus.transport.infrastructure.messaging.TripCancellationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class TripCancellationOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(
            TripCancellationOutboxRelay.class
    );

    private final MyBatisOutboxRelayRepository repository;
    private final TripCancellationEventPublisher publisher;
    private final OutboxRelayProperties properties;
    private final Clock clock;

    public TripCancellationOutboxRelay(
            MyBatisOutboxRelayRepository repository,
            TripCancellationEventPublisher publisher,
            OutboxRelayProperties properties,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    public OutboxRelayResult relayReadyEvents() {
        OutboxRelayResult requested = relayType(
                "transport",
                TripCancellationRequestedEvent.TYPE
        );
        OutboxRelayResult settled = relayType(
                "booking",
                TripCancellationBookingsSettledEvent.TYPE
        );
        return new OutboxRelayResult(
                requested.claimed() + settled.claimed(),
                requested.published() + settled.published(),
                requested.failed() + settled.failed()
        );
    }

    private OutboxRelayResult relayType(String context, String eventType) {
        List<ClaimedOutboxEvent> events = repository.claimReady(
                context,
                eventType,
                clock.instant(),
                properties.batchSize(),
                properties.claimTimeout()
        );
        int published = 0;
        int failed = 0;
        for (ClaimedOutboxEvent event : events) {
            try {
                publisher.publish(event);
                repository.markPublished(event, clock.instant());
                published++;
            } catch (RuntimeException exception) {
                failed++;
                markFailed(event, exception);
            }
        }
        return new OutboxRelayResult(events.size(), published, failed);
    }

    private void markFailed(
            ClaimedOutboxEvent event,
            RuntimeException publishFailure
    ) {
        int nextAttempt = event.retryCount() + 1;
        Instant retryAt = nextAttempt >= properties.maximumAttempts()
                ? null
                : clock.instant().plus(retryDelay(nextAttempt));
        try {
            repository.markFailed(event, retryAt);
        } catch (RuntimeException markingFailure) {
            publishFailure.addSuppressed(markingFailure);
        }
        log.warn(
                "Trip cancellation event {} publish failed; retryAt={}",
                event.eventId(),
                retryAt,
                publishFailure
        );
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 20);
        Duration candidate;
        try {
            candidate = properties.initialRetryDelay()
                    .multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return properties.maximumRetryDelay();
        }
        return candidate.compareTo(properties.maximumRetryDelay()) > 0
                ? properties.maximumRetryDelay()
                : candidate;
    }
}
