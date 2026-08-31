package com.schoolbus.transport.infrastructure.outbox;

import com.schoolbus.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.payment.infrastructure.outbox.ClaimedOutboxEvent;
import com.schoolbus.payment.infrastructure.outbox.MyBatisOutboxRelayRepository;
import com.schoolbus.payment.infrastructure.outbox.OutboxRelayResult;
import com.schoolbus.transport.application.trip.TripPublishedEvent;
import com.schoolbus.transport.infrastructure.messaging.TripPublicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
@ConditionalOnProperty(prefix = "school-bus.transport.publication-events", name = "enabled", havingValue = "true")
public class TripPublicationOutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(TripPublicationOutboxRelay.class);
    private final MyBatisOutboxRelayRepository repository;
    private final TripPublicationEventPublisher publisher;
    private final OutboxRelayProperties properties;
    private final Clock clock;

    public TripPublicationOutboxRelay(MyBatisOutboxRelayRepository repository,
            TripPublicationEventPublisher publisher, OutboxRelayProperties properties, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);
    }

    // Deliberately not transactional: claims and completion markers have short DB transactions;
    // waiting for the broker must never hold the publication transaction open.
    public OutboxRelayResult relayReadyEvents() {
        List<ClaimedOutboxEvent> events = repository.claimReady("transport", TripPublishedEvent.TYPE,
                clock.instant(), properties.batchSize(), properties.claimTimeout());
        int published = 0;
        int failed = 0;
        for (ClaimedOutboxEvent event : events) {
            try {
                publisher.publish(event);
                repository.markPublished(event, clock.instant());
                published++;
            } catch (RuntimeException exception) {
                failed++;
                Instant retryAt = event.retryCount() >= properties.maximumAttempts() - 1
                        ? null : clock.instant().plus(retryDelay(event.retryCount()));
                try {
                    repository.markFailed(event, retryAt);
                } catch (RuntimeException markingFailure) {
                    exception.addSuppressed(markingFailure);
                }
                log.warn("TripPublished event {} failed; retryAt={}", event.eventId(), retryAt, exception);
            }
        }
        return new OutboxRelayResult(events.size(), published, failed);
    }

    private Duration retryDelay(int previousAttempts) {
        Duration delay;
        try {
            delay = properties.initialRetryDelay().multipliedBy(1L << Math.min(previousAttempts, 20));
        } catch (ArithmeticException exception) {
            return properties.maximumRetryDelay();
        }
        return delay.compareTo(properties.maximumRetryDelay()) > 0 ? properties.maximumRetryDelay() : delay;
    }
}
