package com.schoolbus.paymentservice.infrastructure.outbox;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class MyBatisOutboxRelayRepository {

    private static final ZoneOffset DATABASE_ZONE = ZoneOffset.UTC;
    private static final String CONTEXT_NAME = "payment";
    private static final String EVENT_TYPE = "PaymentRefundRequired";

    private final OutboxMapper mapper;

    public MyBatisOutboxRelayRepository(OutboxMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Transactional
    public List<ClaimedOutboxEvent> claimReady(
            Instant now,
            int batchSize,
            Duration claimTimeout
    ) {
        return claimReady(
                CONTEXT_NAME,
                EVENT_TYPE,
                now,
                batchSize,
                claimTimeout
        );
    }

    @Transactional
    public List<ClaimedOutboxEvent> claimReady(
            String contextName,
            String eventType,
            Instant now,
            int batchSize,
            Duration claimTimeout
    ) {
        String checkedContext = requireText(contextName, "contextName");
        String checkedEventType = requireText(eventType, "eventType");
        Instant checkedNow = Objects.requireNonNull(now, "now must not be null");
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Duration checkedTimeout = requirePositive(
                claimTimeout,
                "claimTimeout"
        );
        LocalDateTime readyAt = databaseTime(checkedNow);
        LocalDateTime claimedUntil = databaseTime(
                checkedNow.plus(checkedTimeout)
        );
        List<ClaimedOutboxEvent> claimed = new ArrayList<>();
        for (OutboxEventDataObject candidate : mapper.selectRelayCandidates(
                checkedContext,
                checkedEventType,
                readyAt,
                batchSize
        )) {
            int updated = mapper.tryClaim(
                    candidate.getId(),
                    candidate.getVersion(),
                    claimedUntil
            );
            if (updated == 1) {
                claimed.add(toClaimedEvent(candidate));
            }
        }
        return List.copyOf(claimed);
    }

    @Transactional
    public void markPublished(
            ClaimedOutboxEvent event,
            Instant publishedAt
    ) {
        ClaimedOutboxEvent checked = Objects.requireNonNull(
                event,
                "event must not be null"
        );
        int updated = mapper.markPublished(
                checked.id(),
                checked.claimedVersion(),
                databaseTime(publishedAt)
        );
        ensureUpdated(updated, checked);
    }

    @Transactional
    public void markFailed(
            ClaimedOutboxEvent event,
            Instant nextRetryAt
    ) {
        ClaimedOutboxEvent checked = Objects.requireNonNull(
                event,
                "event must not be null"
        );
        int updated = mapper.markFailed(
                checked.id(),
                checked.claimedVersion(),
                nextRetryAt == null ? null : databaseTime(nextRetryAt)
        );
        ensureUpdated(updated, checked);
    }

    private ClaimedOutboxEvent toClaimedEvent(
            OutboxEventDataObject source
    ) {
        return new ClaimedOutboxEvent(
                source.getId(),
                source.getEventId(),
                source.getEventType(),
                source.getPayload(),
                source.getTraceId(),
                source.getRetryCount(),
                source.getOccurredAt().toInstant(DATABASE_ZONE),
                source.getVersion() + 1
        );
    }

    private void ensureUpdated(
            int updated,
            ClaimedOutboxEvent event
    ) {
        if (updated != 1) {
            throw new IllegalStateException(
                    "outbox claim was lost for event " + event.eventId()
            );
        }
    }

    private LocalDateTime databaseTime(Instant instant) {
        return LocalDateTime.ofInstant(
                Objects.requireNonNull(instant, "instant must not be null"),
                DATABASE_ZONE
        );
    }

    private Duration requirePositive(Duration duration, String name) {
        Duration checked = Objects.requireNonNull(
                duration,
                name + " must not be null"
        );
        if (checked.isZero() || checked.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
