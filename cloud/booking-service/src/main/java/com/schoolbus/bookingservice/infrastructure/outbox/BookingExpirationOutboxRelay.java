package com.schoolbus.bookingservice.infrastructure.outbox;

import com.schoolbus.bookingservice.application.booking.BookingPaymentDeadlineEvent;
import com.schoolbus.bookingservice.infrastructure.messaging.BookingExpirationEventPublisher;
import com.schoolbus.bookingservice.support.payment.infrastructure.messaging.OutboxRelayProperties;
import com.schoolbus.bookingservice.support.payment.infrastructure.outbox.ClaimedOutboxEvent;
import com.schoolbus.bookingservice.support.payment.infrastructure.outbox.MyBatisOutboxRelayRepository;
import com.schoolbus.bookingservice.support.payment.infrastructure.outbox.OutboxRelayResult;
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
public class BookingExpirationOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(
            BookingExpirationOutboxRelay.class
    );
    private static final String CONTEXT_NAME = "booking";

    private final MyBatisOutboxRelayRepository repository;
    private final BookingExpirationEventPublisher publisher;
    private final OutboxRelayProperties properties;
    private final Clock clock;

    public BookingExpirationOutboxRelay(
            MyBatisOutboxRelayRepository repository,
            BookingExpirationEventPublisher publisher,
            OutboxRelayProperties properties,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(
                repository,
                "repository must not be null"
        );
        this.publisher = Objects.requireNonNull(
                publisher,
                "publisher must not be null"
        );
        this.properties = Objects.requireNonNull(
                properties,
                "properties must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public OutboxRelayResult relayReadyEvents() {
        Instant claimedAt = clock.instant();
        List<ClaimedOutboxEvent> events = repository.claimReady(
                CONTEXT_NAME,
                BookingPaymentDeadlineEvent.TYPE,
                claimedAt,
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
        if (retryAt == null) {
            log.error(
                    "Booking expiration event {} exhausted {} publish attempts",
                    event.eventId(),
                    properties.maximumAttempts(),
                    publishFailure
            );
        } else {
            log.warn(
                    "Booking expiration event {} publish failed; next attempt at {}",
                    event.eventId(),
                    retryAt,
                    publishFailure
            );
        }
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
