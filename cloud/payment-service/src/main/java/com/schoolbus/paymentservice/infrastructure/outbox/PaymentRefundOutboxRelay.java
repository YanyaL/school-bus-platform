package com.schoolbus.paymentservice.infrastructure.outbox;

import com.schoolbus.paymentservice.infrastructure.messaging.OutboxEventPublisher;
import com.schoolbus.paymentservice.infrastructure.messaging.OutboxRelayProperties;
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
public class PaymentRefundOutboxRelay {

    private static final String PAYMENT_CONTEXT = "payment";
    private static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";

    private static final Logger log = LoggerFactory.getLogger(
            PaymentRefundOutboxRelay.class
    );

    private final MyBatisOutboxRelayRepository repository;
    private final OutboxEventPublisher publisher;
    private final OutboxRelayProperties properties;
    private final Clock clock;

    public PaymentRefundOutboxRelay(
            MyBatisOutboxRelayRepository repository,
            OutboxEventPublisher publisher,
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
        List<ClaimedOutboxEvent> refundEvents = repository.claimReady(
                claimedAt,
                properties.batchSize(),
                properties.claimTimeout()
        );
        List<ClaimedOutboxEvent> succeededEvents = repository.claimReady(
                PAYMENT_CONTEXT,
                PAYMENT_SUCCEEDED,
                claimedAt,
                properties.batchSize(),
                properties.claimTimeout()
        );
        List<ClaimedOutboxEvent> events = new java.util.ArrayList<>(
                refundEvents.size() + succeededEvents.size()
        );
        events.addAll(refundEvents);
        events.addAll(succeededEvents);
        events.sort(java.util.Comparator.comparingLong(
                ClaimedOutboxEvent::id
        ));
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
                    "Outbox event {} exhausted {} publish attempts",
                    event.eventId(),
                    properties.maximumAttempts(),
                    publishFailure
            );
        } else {
            log.warn(
                    "Outbox event {} publish failed; next attempt at {}",
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
